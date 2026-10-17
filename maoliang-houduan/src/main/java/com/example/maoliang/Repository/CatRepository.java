package com.example.maoliang.Repository;

import com.example.maoliang.Entity.Cat;
import com.example.maoliang.Entity.Good;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//连接到MLGood的其他数据库操作方法
@Repository
public class CatRepository {
// Spring 的 JdbcTemplate方式
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public CatRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Cat findCatById(int owner) {
        String sql = "SELECT * FROM MLcat WHERE owner = ? ORDER BY catid LIMIT 1";
        List<Cat> cats = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(Cat.class), owner);
        return cats.isEmpty() ? null : cats.get(0);
    }

    public List<Good> findGoodsByCatAge(int newcatage) {
        String sqlQuery = "SELECT goodid, goodname, price, picture, calorie, catweight, kind, state, number FROM MLgood " +
                "WHERE CAST(SUBSTRING(catage, 1, POSITION('-' IN catage) - 1) AS SIGNED) <= ? " +
                "AND CAST(SUBSTRING(catage, POSITION('-' IN catage) + 1) AS SIGNED) >= ?";

        List<Good> goods = new ArrayList<>();

        try {
            goods = jdbcTemplate.query(sqlQuery, new Object[]{newcatage, newcatage}, new RowMapper<Good>() {
                @Override
                public Good mapRow(ResultSet rs, int rowNum) throws SQLException {
                    Good good = new Good();
                    good.setGoodid(rs.getInt("goodid"));
                    good.setGoodname(rs.getString("goodname"));
                    good.setPrice(rs.getDouble("price"));
                    good.setPicture(rs.getString("picture"));
                    good.setCalorie(rs.getDouble("calorie"));
                    good.setCatweight(rs.getString("catweight"));
                    good.setKind(rs.getString("kind"));
                    good.setState(rs.getInt("state"));
                    good.setNumber(rs.getInt("number"));
                    return good;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        return goods;
    }

    public List<Good> findGoodsByCatWeight(double newcatweight) {
        String sqlQuery = "SELECT goodid, goodname, price, picture, calorie, catweight, kind, state, number FROM MLgood " +
                "WHERE CAST(SUBSTRING(catweight, 1, POSITION('-' IN catweight) - 1) AS DOUBLE) <= ? " +
                "AND CAST(SUBSTRING(catweight, POSITION('-' IN catweight) + 1) AS DOUBLE) >= ?";

        List<Good> goods = new ArrayList<>();

        try {
            goods = jdbcTemplate.query(sqlQuery, new Object[]{newcatweight, newcatweight}, new RowMapper<Good>() {
                @Override
                public Good mapRow(ResultSet rs, int rowNum) throws SQLException {
                    Good good = new Good();
                    good.setGoodid(rs.getInt("goodid"));
                    good.setGoodname(rs.getString("goodname"));
                    good.setPrice(rs.getDouble("price"));
                    good.setPicture(rs.getString("picture"));
                    good.setCalorie(rs.getDouble("calorie"));
                    good.setCatweight(rs.getString("catweight"));
                    good.setKind(rs.getString("kind"));
                    good.setState(rs.getInt("state"));
                    good.setNumber(rs.getInt("number"));
                    return good;
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        return goods;
    }

    public List<Good> findGoodsByCatKind(String catkind) {
        String sqlQuery = "SELECT goodid, goodname, price, picture, calorie, catweight, kind, state, number " +
                "FROM MLgood " +
                "WHERE catkind LIKE ?";

        String likePattern = "%" + catkind + "%";

        List<Good> goods = new ArrayList<>();

        try {
            goods = jdbcTemplate.query(sqlQuery, new Object[]{likePattern}, new RowMapper<Good>() {
                @Override
                public Good mapRow(ResultSet rs, int rowNum) throws SQLException {
                    Good good = new Good();
                 //   System.out.println(rs.getInt("goodid"));
                    good.setGoodid(rs.getInt("goodid"));
                    good.setGoodname(rs.getString("goodname"));
                    good.setPrice(rs.getDouble("price"));
                    good.setPicture(rs.getString("picture"));
                    good.setCalorie(rs.getDouble("calorie"));
                    good.setCatweight(rs.getString("catweight"));
                    good.setKind(rs.getString("kind"));
                    good.setState(rs.getInt("state"));
                    good.setNumber(rs.getInt("number"));
                    return good;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }


        return goods;
    }


    public void save(Cat cat) {
        String sql = "INSERT INTO MLcat (catname, description, catweight, catstate, catage, catkind, owner) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, cat.getCatname(), cat.getDescription(),
                cat.getCatweight(), cat.getCatstate(), cat.getCatage(), cat.getCatkind(), cat.getOwner());
    }

    public int update(Cat cat) {
        String sql = "UPDATE MLcat SET catname = ?, description = ?, catkind = ?, catage = ?, catweight = ?, catstate = ? WHERE catid = ? AND owner = ?";
        return jdbcTemplate.update(sql, cat.getCatname(), cat.getDescription(), cat.getCatkind(),
                cat.getCatage(), cat.getCatweight(), cat.getCatstate(), cat.getCatid(), cat.getOwner());
    }

    public int delete(int catid, int owner) {
        return jdbcTemplate.update("DELETE FROM MLcat WHERE catid = ? AND owner = ?", catid, owner);
    }


    public List<Cat> findByOwner(int ownerId) {
        String sqlQuery = "SELECT * FROM MLcat WHERE owner = ? ORDER BY catid DESC";
        return jdbcTemplate.query(sqlQuery, new Object[]{ownerId}, (rs, rowNum) -> {
            Cat cat = new Cat();
            cat.setCatid(rs.getInt("catid"));
            cat.setCatname(rs.getString("catname"));
            cat.setDescription(rs.getString("description"));
            cat.setCatweight(rs.getDouble("catweight"));
            cat.setCatstate(rs.getInt("catstate"));
            cat.setCatage(rs.getInt("catage"));
            cat.setCatkind(rs.getString("catkind"));
            cat.setOwner(rs.getInt("owner"));
            return cat;
        });
    }
    public Cat findById(int catid) {
        String query = "SELECT * FROM MLcat WHERE catid = ?";
        return jdbcTemplate.queryForObject(query, new Object[]{catid}, (rs, rowNum) -> {
            Cat cat = new Cat();
            cat.setCatid(rs.getInt("catid"));
            cat.setCatname(rs.getString("catname"));
            cat.setDescription(rs.getString("description"));
            cat.setCatweight(rs.getDouble("catweight"));
            cat.setCatstate(rs.getInt("catstate"));
            cat.setCatage(rs.getInt("catage"));
            cat.setCatkind(rs.getString("catkind"));
            cat.setOwner(rs.getInt("owner"));
            return cat;
        });
    }

    public Cat findByIdAndOwner(int catid, int owner) {
        List<Cat> cats = jdbcTemplate.query("SELECT * FROM MLcat WHERE catid = ? AND owner = ?",
                new BeanPropertyRowMapper<>(Cat.class), catid, owner);
        return cats.isEmpty() ? null : cats.get(0);
    }
}
