package com.example.maoliang.Repository;

import com.example.maoliang.Entity.Good;
import com.example.maoliang.dto.GoodPage;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

//输出为List<Good>的数据库操作方法
@Repository
public class GoodListRepository {
// Spring 的 JdbcTemplate方式
    private final JdbcTemplate jdbcTemplate;

    public GoodListRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Good> shownow() {
        String sql = "SELECT * FROM MLgood WHERE state = 0 ORDER BY goodid";
        try {
            return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(Good.class));
        } catch (EmptyResultDataAccessException e) {
            // 如果查询结果为空，返回 null
            return null;
        }
    }

    public GoodPage showBuyerPage(String category, int page, int size) {
        boolean allCategories = category == null || category.isBlank() || "全部".equals(category);
        String condition = allCategories ? "state = 0" : "state = 0 AND kind = ?";
        long total = allCategories
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM MLgood WHERE " + condition, Long.class)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM MLgood WHERE " + condition, Long.class, category);
        String sql = "SELECT * FROM MLgood WHERE " + condition + " ORDER BY goodid LIMIT ? OFFSET ?";
        long offset = ((long) page - 1) * size;
        List<Good> items = allCategories
                ? jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(Good.class), size, offset)
                : jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(Good.class), category, size, offset);
        return new GoodPage(items, total);
    }

    public List<Good> showHotGoods() {
        // Aggregate separately so joining orders and favourites cannot multiply sales.
        String sql = """
                SELECT g.* FROM MLgood g
                LEFT JOIN (SELECT goodid, SUM(number) AS sales FROM MLorder
                           WHERE orderstate > 0 GROUP BY goodid) o ON o.goodid = g.goodid
                LEFT JOIN (SELECT goodid, COUNT(DISTINCT buyer) AS favourites FROM MLbuying
                           WHERE islike = 1 GROUP BY goodid) b ON b.goodid = g.goodid
                WHERE g.state = 0 AND g.number > 0
                ORDER BY COALESCE(o.sales, 0) DESC, COALESCE(b.favourites, 0) DESC, g.goodid DESC
                LIMIT 8
                """;
        return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(Good.class));
    }


    public List<Good> searchls(String keyword, String kind, int power, int userid, int ishistory) {
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM ");

        if (ishistory == 1) {
            sqlBuilder.append("MLhistorygood");
            if (power == 1) {
                sqlBuilder.append(" WHERE owner = ?");
            } else {
                sqlBuilder.append(" WHERE kind = ?");
            }

        } else {
            sqlBuilder.append("MLgood WHERE state = 0");
            if (power == 1) {
                sqlBuilder.append(" AND owner = ?");
            } else {
                sqlBuilder.append(" AND kind = ?");
            }

        }

        sqlBuilder.append(" AND goodname LIKE ?");
        String sql = sqlBuilder.toString();
        try {
            return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(Good.class), (power == 1) ? userid : kind, "%" + keyword + "%");
        } catch (EmptyResultDataAccessException e) {
            // 如果查询结果为空，返回 null
            return null;
        }
    }


    public List<Good> showall(int userid) {
        String sql = "SELECT * FROM MLgood WHERE owner = ?";
        try {
            return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(Good.class), userid);
        } catch (EmptyResultDataAccessException e) {
            // 如果查询结果为空，返回 null
            return null;
        }
    }

    public List<Good> showhistoryall(int userid) {
        String sql = "SELECT * FROM MLhistorygood WHERE owner = ?";
        try {
            return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(Good.class), userid);
        } catch (EmptyResultDataAccessException e) {
            // 如果查询结果为空，返回 null
            return null;
        }
    }

    public List<Good> showlike(int userid) {
        String sql = "SELECT b.*, g.price * b.number AS totalprice FROM MLbuying b INNER JOIN MLgood g ON b.goodid = g.goodid WHERE b.buyer = ? AND b.islike = 1";
        try {
            return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(Good.class), userid);
        } catch (
        EmptyResultDataAccessException e) {
            // 如果查询结果为空，返回 null
            return null;
        }
    }

    public List<Good> showbuyerall(int userid) {
        String sql = "SELECT b.*, g.price * b.number AS totalprice FROM MLbuying b INNER JOIN MLgood g ON b.goodid = g.goodid WHERE b.buyer = ? AND b.number > 0";
        try {
            return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(Good.class), userid);
        } catch (EmptyResultDataAccessException e) {
            // 如果查询结果为空，返回 null
            return null;
        }
    }

}
