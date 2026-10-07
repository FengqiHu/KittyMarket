package com.example.maoliang.Service;


import com.example.maoliang.Entity.Order;
import com.example.maoliang.Entity.Good;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Repository.GoodRepository;
import com.example.maoliang.dto.CreateOrderData;
import com.example.maoliang.Repository.OrderListRepository;
import com.example.maoliang.Repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    OrderListRepository orderListRepository;

    @Autowired
    private GoodRepository goodRepository;

    @Transactional
    public Order createOrder(CreateOrderData request, Usr buyer) {
        if (buyer == null) throw new IllegalArgumentException("请先登录后再下单");
        if (request.goodid() == null || request.goodid() < 1 || request.number() == null || request.number() < 1) {
            throw new IllegalArgumentException("请选择有效商品和购买数量");
        }
        String recipient = requireText(request.buyerName(), 10, "收货人");
        String address = requireText(request.address(), 99, "收货地址");
        String telephone = request.telephone() == null ? "" : request.telephone().trim();
        if (!telephone.matches("[0-9]{11}")) throw new IllegalArgumentException("联系电话需要是 11 位数字");

        Good good = goodRepository.lockGood(request.goodid());
        if (good == null || good.getState() != 0) throw new IllegalArgumentException("商品已下架或不存在");
        if (!goodRepository.reserveStock(good.getGoodid(), request.number())) {
            throw new IllegalArgumentException("库存不足，请减少购买数量");
        }

        Order order = new Order();
        order.setBuyername(buyer.getUsername().trim());
        order.setRecipientname(recipient);
        order.setAddress(address);
        order.setTelephone(telephone);
        order.setGoodid(good.getGoodid());
        order.setNumber(request.number());
        order.setOwner(good.getOwner());
        orderRepository.create(order);
        if (Boolean.TRUE.equals(request.fromCart())) {
            goodRepository.consumeCart(good.getGoodid(), buyer.getUserid(), request.number());
        }
        return order;
    }

    private String requireText(String value, int maxLength, String label) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw new IllegalArgumentException(label + "不能为空且不能超过 " + maxLength + " 个字符");
        }
        return text;
    }


    public List<Order> showOrderInformation() {
        return orderListRepository.searchOrderInformation();
    }


    public void deletegood(int goodid) {
        orderRepository.deletegood(goodid);
    }

    @Transactional
    public boolean deleteOrder(int orderid, int orderstate, Usr user) {
        if (user == null || orderstate != -1) return false;
        Order order = orderRepository.lockOrder(orderid);
        if (order == null || order.getOrderstate() < 1 || order.getOrderstate() >= 4) return false;
        boolean buyer = order.getBuyername().trim().equals(user.getUsername().trim());
        boolean seller = order.getOwner() == user.getUserid();
        if (!buyer && !seller) return false;
        orderRepository.cancel(orderid);
        if (!goodRepository.updateGoodNumber(order.getNumber(), order.getGoodid())) {
            throw new IllegalStateException("取消订单时恢复库存失败");
        }
        return true;
    }

    public boolean confirmOrder(int orderid, int orderstate) {
        return orderListRepository.confirmOrder(orderid, orderstate);
    }

    public boolean buyerhistoryconfirmOrder(int orderid, int orderstate) {
        return orderListRepository.buyerhistoryconfirmOrder(orderid, orderstate);
    }

    public List<Order> showbuyerorderinfo(String name) {
        return  orderListRepository.showall(name);
    }


}
