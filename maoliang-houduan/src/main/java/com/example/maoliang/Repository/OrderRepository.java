package com.example.maoliang.Repository;

import com.example.maoliang.Entity.Order;
import com.example.maoliang.Entity.Usr;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

//连接到MLorder的其他数据库操作方法
@Repository
public class OrderRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void add(Order order) {
        String sql = "INSERT INTO MLorder(address, telephone, buyername, goodid, number, orderstate, owner) " +
                "VALUES (?, ?, ?, ?, ?, 1, ?)";
        jdbcTemplate.update(sql, order.getAddress(), order.getTelephone(), order.getBuyername(),
                order.getGoodid(), order.getNumber(), order.getOwner());
    }

    public Order create(Order order) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO MLorder(address, telephone, buyername, recipientname, goodid, number, orderstate, owner) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 1, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, order.getAddress());
            statement.setString(2, order.getTelephone());
            statement.setString(3, order.getBuyername());
            statement.setString(4, order.getRecipientname());
            statement.setInt(5, order.getGoodid());
            statement.setInt(6, order.getNumber());
            statement.setInt(7, order.getOwner());
            return statement;
        }, key);
        order.setOrderid(key.getKey().intValue());
        order.setOrderstate(1);
        return order;
    }

    public Order lockOrder(int orderid) {
        List<Order> orders = jdbcTemplate.query("SELECT * FROM MLorder WHERE orderid = ? FOR UPDATE",
                org.springframework.jdbc.core.BeanPropertyRowMapper.newInstance(Order.class), orderid);
        return orders.isEmpty() ? null : orders.get(0);
    }

    public void cancel(int orderid) {
        jdbcTemplate.update("UPDATE MLorder SET orderstate = -1 WHERE orderid = ?", orderid);
    }

    public void modifystate(int orderid, int tostate) {
        String sql = "UPDATE MLorder SET orderstate = ? WHERE orderid = ?";
        jdbcTemplate.update(sql, tostate, orderid);
    }

    public void deletegood(int goodid) {
        String sql = "DELETE FROM MLorder WHERE goodid = ?";
        jdbcTemplate.update(sql, goodid);
    }

    public int searchid(int orderid) {
        String sql = "SELECT goodid FROM MLorder WHERE orderid = ?";
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, orderid);
        } catch (EmptyResultDataAccessException e) {
            // 查询无结果时返回-1
            return -1;
        }
    }

    public void deleteorder(int orderid) {
        String sql = "UPDATE MLorder SET orderstate = 1 WHERE orderid = ?";
        jdbcTemplate.update(sql, orderid);
    }

    public int searchstate(int orderid) {
        String sql = "SELECT orderstate FROM MLorder WHERE orderid = ?";
        try {
             return jdbcTemplate.queryForObject(sql, Integer.class, orderid);
        } catch (EmptyResultDataAccessException e) {
            // 查询无结果时返回-1
            return -1;
        }
    }

    public Order getOrderById(int orderId) {
        String sql = "SELECT * FROM MLorder WHERE orderid = ?";
        try{
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Order resultOrder = new Order();
                resultOrder.setOrderid(rs.getInt("orderid"));
                resultOrder.setAddress(rs.getString("address"));
                resultOrder.setTelephone(rs.getString("telephone"));
                resultOrder.setBuyername(rs.getString("buyername"));
                resultOrder.setRecipientname(rs.getString("recipientname"));
                resultOrder.setGoodid(rs.getInt("goodid"));
                resultOrder.setNumber(rs.getInt("number"));
                resultOrder.setOrderstate(rs.getInt("orderstate"));
                resultOrder.setOwner(rs.getInt("owner"));
                return resultOrder;
            }, orderId);
        } catch (EmptyResultDataAccessException e) {
            // 查询无结果时返回null
            return null;
        }
    }
}
