package com.example.maoliang.Repository;

import com.example.maoliang.Entity.Good;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.dto.GoodPage;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Ranks the whole available catalogue before applying pagination. No user results are shared. */
@Repository
public class RecommendationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public RecommendationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String POPULARITY_JOINS = """
            LEFT JOIN (SELECT goodid, SUM(number) sales FROM MLorder
                       WHERE orderstate > 0 GROUP BY goodid) sales ON sales.goodid = g.goodid
            LEFT JOIN (SELECT goodid, COUNT(DISTINCT buyer) favourites FROM MLbuying
                       WHERE islike = 1 GROUP BY goodid) likes ON likes.goodid = g.goodid
            """;

    // Purchases identify the buyer by account name; MLorder.owner is the seller.
    // Recent views decay over time and repeat views are capped. Category preferences are
    // normalised so a long history cannot drown out the cat profile or other signals.
    private static final String PREFERENCE_CTES = """
            WITH signals AS (
                SELECT goodid, LEAST(visits, 5) /
                    (1 + TIMESTAMPDIFF(DAY, last_viewed, CURRENT_TIMESTAMP) / 7.0) weight
                FROM MLproduct_view WHERE userid = :userid
                    AND last_viewed >= CURRENT_TIMESTAMP - INTERVAL 90 DAY
                UNION ALL
                SELECT goodid, 6 * LEAST(SUM(number), 5) weight FROM MLorder
                    WHERE buyername = :username AND orderstate > 0 GROUP BY goodid
                UNION ALL
                SELECT goodid, 3 weight FROM MLbuying
                    WHERE buyer = :userid AND islike = 1 GROUP BY goodid
            ), product_preferences AS (
                SELECT goodid, SUM(weight) weight FROM signals GROUP BY goodid
            ), kind_preferences AS (
                SELECT g.kind, SUM(s.weight) weight FROM signals s
                    JOIN MLgood g ON g.goodid = s.goodid GROUP BY g.kind
            ), subkind_preferences AS (
                SELECT g.kind, g.subkind, SUM(s.weight) weight FROM signals s
                    JOIN MLgood g ON g.goodid = s.goodid
                    WHERE TRIM(g.subkind) NOT IN ('', '默认子类') GROUP BY g.kind, g.subkind
            ), signal_total AS (SELECT COALESCE(SUM(weight), 0) weight FROM signals)
            """;

    // Invalid or missing ranges give no fit bonus. Explicit age/weight mismatches
    // lower the rank, while all products remain accessible. Use the best fit across cats.
    private static final String CAT_SCORE = """
            COALESCE((SELECT MAX(
                CASE WHEN TRIM(g.catage) REGEXP '^[0-9]+([.][0-9]+)? *- *[0-9]+([.][0-9]+)?$'
                    THEN CASE WHEN c.catage BETWEEN
                        CAST(SUBSTRING_INDEX(g.catage, '-', 1) AS DECIMAL(10,2)) AND
                        CAST(SUBSTRING_INDEX(g.catage, '-', -1) AS DECIMAL(10,2))
                        THEN 4 ELSE -20 END ELSE 0 END
                + CASE WHEN TRIM(g.catweight) REGEXP '^[0-9]+([.][0-9]+)? *- *[0-9]+([.][0-9]+)?$'
                    THEN CASE WHEN c.catweight BETWEEN
                        CAST(SUBSTRING_INDEX(g.catweight, '-', 1) AS DECIMAL(10,2)) AND
                        CAST(SUBSTRING_INDEX(g.catweight, '-', -1) AS DECIMAL(10,2))
                        THEN 3 ELSE -10 END ELSE 0 END
                + CASE WHEN TRIM(c.catkind) <> '' AND LOCATE(TRIM(c.catkind), g.catkind) > 0 THEN 3
                    WHEN g.catkind REGEXP '通用|全猫' THEN 1 ELSE 0 END
                + CASE WHEN CONCAT(g.goodname, ' ', g.description) REGEXP
                    CASE c.catstate
                        WHEN 1 THEN '减重|体重管理|低脂|控重'
                        WHEN 2 THEN '室内|低脂|体重管理'
                        WHEN 3 THEN '绝育|成猫'
                        WHEN 4 THEN '成猫'
                        WHEN 5 THEN '妊娠|孕猫|孕期|母猫'
                        WHEN 6 THEN '哺乳|母猫'
                        WHEN 7 THEN '幼猫|奶糕|离乳'
                        ELSE 'a^' END THEN 6 ELSE 0 END
            ) FROM MLcat c WHERE c.owner = :userid), 0)
            """;

    public GoodPage page(String category, int page, int size, Usr user) {
        boolean personalised = user != null && user.getPower() == 0;
        var params = new MapSqlParameterSource()
                .addValue("size", size).addValue("offset", ((long) page - 1) * size);
        String condition = "g.state = 0";
        if (category != null && !category.isBlank() && !"全部".equals(category)) {
            condition += " AND g.kind = :category";
            params.addValue("category", category);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM MLgood g WHERE " + condition, params, Long.class);
        String score = "2 * LN(1 + COALESCE(sales.sales, 0)) + LN(1 + COALESCE(likes.favourites, 0))";
        String joins = POPULARITY_JOINS;
        if (personalised) {
            params.addValue("userid", user.getUserid()).addValue("username", user.getUsername().trim());
            joins += """
                    LEFT JOIN product_preferences pp ON pp.goodid = g.goodid
                    LEFT JOIN kind_preferences kp ON kp.kind = g.kind
                    LEFT JOIN subkind_preferences sp ON sp.kind = g.kind AND sp.subkind = g.subkind
                    CROSS JOIN signal_total st
                    """;
            score += " + 3 * LN(1 + COALESCE(pp.weight, 0))"
                    + " + 3 * COALESCE(kp.weight / NULLIF(st.weight, 0), 0)"
                    + " + 8 * COALESCE(sp.weight / NULLIF(st.weight, 0), 0) + " + CAT_SCORE;
        }
        String sql = (personalised ? PREFERENCE_CTES : "")
                + "SELECT g.* FROM MLgood g " + joins + " WHERE " + condition
                + " ORDER BY (g.number > 0) DESC, (" + score + ") DESC, g.goodid DESC LIMIT :size OFFSET :offset";
        return new GoodPage(jdbc.query(sql, params, BeanPropertyRowMapper.newInstance(Good.class)), total);
    }

    public void recordView(int userId, int goodId) {
        jdbc.update("""
                INSERT INTO MLproduct_view (userid, goodid, visits, last_viewed)
                SELECT :userid, goodid, 1, CURRENT_TIMESTAMP FROM MLgood
                WHERE goodid = :goodid AND state = 0
                ON DUPLICATE KEY UPDATE
                    visits = IF(last_viewed < CURRENT_TIMESTAMP - INTERVAL 30 SECOND, LEAST(visits + 1, 20), visits),
                    last_viewed = CURRENT_TIMESTAMP
                """, new MapSqlParameterSource().addValue("userid", userId).addValue("goodid", goodId));
    }
}
