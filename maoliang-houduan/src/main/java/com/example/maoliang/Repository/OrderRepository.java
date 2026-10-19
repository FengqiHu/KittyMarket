package com.example.maoliang.Repository;

import com.example.maoliang.Entity.Order;
import com.example.maoliang.dto.OrderItem;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

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
        int orderId = key.getKey().intValue();
        order.setOrderid(orderId);
        order.setOrderstate(1);

        List<OrderItem> items = order.getItems();
        if (items != null && !items.isEmpty()) {
            for (OrderItem item : items) {
                jdbcTemplate.update(
                        "INSERT INTO MLorder_item(orderid, goodid, goodname, price, number) VALUES (?, ?, ?, ?, ?)",
                        orderId, item.goodid(), item.goodname(), item.price(), item.number());
            }
        } else {
            jdbcTemplate.update("""
                    INSERT INTO MLorder_item(orderid, goodid, goodname, price, number)
                    SELECT ?, ?, COALESCE((SELECT goodname FROM MLgood WHERE goodid = ?), CONCAT('商品#', ?)),
                           COALESCE((SELECT price FROM MLgood WHERE goodid = ?), 0), ?
                    """, orderId, order.getGoodid(), order.getGoodid(), order.getGoodid(), order.getGoodid(), order.getNumber());
        }
        return order;
    }

    public List<OrderItem> findItemsByOrderId(int orderid) {
        List<OrderItem> items = jdbcTemplate.query("""
                SELECT oi.goodid, oi.goodname, oi.price, oi.number, COALESCE(g.picture, '') AS picture
                FROM MLorder_item oi
                LEFT JOIN MLgood g ON g.goodid = oi.goodid
                WHERE oi.orderid = ?
                ORDER BY oi.id ASC
                """, (rs, rowNum) -> new OrderItem(
                rs.getInt("goodid"),
                rs.getString("goodname"),
                rs.getDouble("price"),
                rs.getInt("number"),
                rs.getString("picture")
        ), orderid);
        if (!items.isEmpty()) return items;
        return jdbcTemplate.query("""
                SELECT o.goodid, COALESCE(g.goodname, CONCAT('商品#', o.goodid)) AS goodname,
                       COALESCE(g.price, 0) AS price, o.number, COALESCE(g.picture, '') AS picture
                FROM MLorder o
                LEFT JOIN MLgood g ON g.goodid = o.goodid
                WHERE o.orderid = ?
                """, (rs, rowNum) -> new OrderItem(
                rs.getInt("goodid"),
                rs.getString("goodname"),
                rs.getDouble("price"),
                rs.getInt("number"),
                rs.getString("picture")
        ), orderid);
    }

    public Order lockOrder(int orderid) {
        List<Order> orders = jdbcTemplate.query("SELECT * FROM MLorder WHERE orderid = ? FOR UPDATE",
                org.springframework.jdbc.core.BeanPropertyRowMapper.newInstance(Order.class), orderid);
        if (orders.isEmpty()) return null;
        Order order = orders.get(0);
        order.setItems(findItemsByOrderId(orderid));
        return order;
    }

    public void cancel(int orderid) {
        jdbcTemplate.update("UPDATE MLorder SET orderstate = -1 WHERE orderid = ?", orderid);
    }

    public void modifystate(int orderid, int tostate) {
        String sql = "UPDATE MLorder SET orderstate = ? WHERE orderid = ?";
        jdbcTemplate.update(sql, tostate, orderid);
    }

    public void deletegood(int goodid) {
        jdbcTemplate.update("DELETE FROM MLorder_item WHERE goodid = ?", goodid);
        jdbcTemplate.update("DELETE FROM MLorder WHERE goodid = ?", goodid);
    }

    public int searchid(int orderid) {
        String sql = "SELECT goodid FROM MLorder WHERE orderid = ?";
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, orderid);
        } catch (EmptyResultDataAccessException e) {
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
            return -1;
        }
    }

    public Order getOrderById(int orderId) {
        String sql = "SELECT * FROM MLorder WHERE orderid = ?";
        try{
            Order order = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
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
            if (order != null) {
                order.setItems(findItemsByOrderId(orderId));
            }
            return order;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}

