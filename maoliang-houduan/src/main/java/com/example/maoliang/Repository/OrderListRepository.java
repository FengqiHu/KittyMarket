package com.example.maoliang.Repository;

import com.example.maoliang.Entity.Order;
import com.example.maoliang.dto.OrderItem;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

//输出为List<Order>的数据库操作方法
@Repository
public class OrderListRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderListRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Order> searchOrderInformation() {
        String sql = "SELECT * FROM MLorder ORDER BY orderid DESC";
        try {
            List<Order> orders = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Order or = new Order();
                or.setOrderid(rs.getInt("orderid"));
                or.setAddress(rs.getString("address"));
                or.setTelephone(rs.getString("telephone"));
                or.setBuyername(rs.getString("buyername"));
                or.setRecipientname(rs.getString("recipientname"));
                or.setGoodid(rs.getInt("goodid"));
                or.setNumber(rs.getInt("number"));
                or.setOrderstate(rs.getInt("orderstate"));
                or.setOwner(rs.getInt("owner"));
                return or;
            });
            attachOrderItems(orders);
            return orders;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<Order> showall2(String buyername) {
        String sql = "SELECT * FROM MLorder WHERE buyername = ? ORDER BY orderid DESC";
        try {
            List<Order> orders = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Order or = new Order();
                or.setOrderid(rs.getInt("orderid"));
                or.setAddress(rs.getString("address"));
                or.setTelephone(rs.getString("telephone"));
                or.setBuyername(rs.getString("buyername"));
                or.setRecipientname(rs.getString("recipientname"));
                or.setGoodid(rs.getInt("goodid"));
                or.setNumber(rs.getInt("number"));
                or.setOrderstate(rs.getInt("orderstate"));
                or.setOwner(rs.getInt("owner"));
                return or;
            }, buyername);
            attachOrderItems(orders);
            return orders;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public boolean deleteOrder(int orderid, int orderstate) {
        if (orderstate == -1) {
            String sql = "UPDATE MLorder SET orderstate =? WHERE orderid =?";
            int affectedRows = jdbcTemplate.update(sql, orderstate, orderid);
            return affectedRows > 0;
        } else {
            return false;
        }
    }

    public boolean confirmOrder(int orderid, int orderstate) {
        String sql = "UPDATE MLorder SET orderstate =? WHERE orderid= ?";
        int affectedRows = jdbcTemplate.update(sql, orderstate, orderid);
        return affectedRows > 0;
    }

    public boolean buyerhistoryconfirmOrder(int orderid, int orderstate) {
        String sql = "UPDATE MLorder SET orderstate =? WHERE orderid= ?";
        int affectedRows = jdbcTemplate.update(sql, orderstate, orderid);
        return affectedRows > 0;
    }

    public List<Order> showall(String name) {
        name = name.trim();
        String sql = "SELECT * FROM MLorder WHERE buyername = ? ORDER BY orderid DESC";
        try {
            List<Order> orders = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Order or = new Order();
                or.setOrderid(rs.getInt("orderid"));
                or.setAddress(rs.getString("address"));
                or.setTelephone(rs.getString("telephone"));
                or.setBuyername(rs.getString("buyername"));
                or.setRecipientname(rs.getString("recipientname"));
                or.setGoodid(rs.getInt("goodid"));
                or.setNumber(rs.getInt("number"));
                or.setOrderstate(rs.getInt("orderstate"));
                or.setOwner(rs.getInt("owner"));
                return or;
            }, name);
            attachOrderItems(orders);
            return orders;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void attachOrderItems(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return;
        for (Order order : orders) {
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
            ), order.getOrderid());
            if (items.isEmpty()) {
                items = jdbcTemplate.query("""
                        SELECT COALESCE(g.goodname, CONCAT('商品#', ?)) AS goodname,
                               COALESCE(g.price, 0) AS price, COALESCE(g.picture, '') AS picture
                        FROM (SELECT 1) dummy
                        LEFT JOIN MLgood g ON g.goodid = ?
                        """, (rs, rowNum) -> new OrderItem(
                        order.getGoodid(),
                        rs.getString("goodname"),
                        rs.getDouble("price"),
                        order.getNumber(),
                        rs.getString("picture")
                ), order.getGoodid(), order.getGoodid());
            }
            order.setItems(items);
        }
    }
}