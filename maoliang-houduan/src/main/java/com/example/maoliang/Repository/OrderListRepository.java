package com.example.maoliang.Repository;

import com.example.maoliang.Entity.Order;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

//输出为List<Order>的数据库操作方法
@Repository
public class OrderListRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderListRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Order> searchOrderInformation() {
        String sql = "SELECT * FROM MLorder";
        try {

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
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
                System.out.println(or);
                return or;
            });
        } catch (EmptyResultDataAccessException e) {
            // 查询无结果时返回null
            return null;
        }
    }


    public List<Order> showall2(String buyername) {
        String sql = "SELECT * FROM MLorder WHERE buyername = ?";
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
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
        } catch (
                EmptyResultDataAccessException e) {
            // 查询无结果时返回null
            return null;
        }
    }


    public boolean deleteOrder(int orderid, int orderstate) {

        if (orderstate == -1) {
            String sql = "UPDATE MLorder SET orderstate =? WHERE orderid =?";
            int affectedRows = jdbcTemplate.update(sql, orderstate, orderid);
            return affectedRows > 0; // 如果影响的行数大于0，则表示删除成功
        } else {
            return false;
        }
    }


    public boolean confirmOrder(int orderid, int orderstate) {
        String sql = "UPDATE MLorder SET orderstate =? WHERE orderid= ?";


        int affectedRows = jdbcTemplate.update(sql, orderstate, orderid);
        return affectedRows > 0; // 如果影响的行数大于0，则表示更新成功
    }
    public boolean buyerhistoryconfirmOrder(int orderid, int orderstate) {
        String sql = "UPDATE MLorder SET orderstate =? WHERE orderid= ?";
        int affectedRows = jdbcTemplate.update(sql, orderstate, orderid);
        return affectedRows > 0; // 如果影响的行数大于0，则表示更新成功
    }

    public  List<Order>  showall(String name) {
        name=name.trim();

        String sql = "SELECT * FROM MLorder WHERE buyername = ?";
        try{
            return  jdbcTemplate.query(sql, (rs, rowNum) -> {
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
        }catch (EmptyResultDataAccessException e){
            return null;
        }
    }


}