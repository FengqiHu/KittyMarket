package com.example.maoliang.Controller;


import com.example.maoliang.Controller.utils.Result;
import com.example.maoliang.Service.OrderService;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.dto.CreateOrderData;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.example.maoliang.Controller.utils.Page.SUCCESS_PAGE;
import static com.example.maoliang.Controller.utils.Page.ERROR_PAGE;

@RestController
@RequestMapping("/order/*")
public class OrderController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);
    @Autowired
    private OrderService orderService;
    @Autowired
    public HttpSession session;

    @PostMapping("/createorder-control")
    public Result createOrderControl(@RequestBody CreateOrderData request) {
        try {
            Usr buyer = (Usr) session.getAttribute("admin");
            return new Result(SUCCESS_PAGE, "下单成功", orderService.createOrder(request, buyer));
        } catch (IllegalArgumentException exception) {
            return new Result(ERROR_PAGE, exception.getMessage(), null);
        }
    }

    @RequestMapping("/showorderinfo-control")
    public Result showOrderInfoControl( ) {
        return new Result(SUCCESS_PAGE, "查看意向订单成功", orderService.showOrderInformation());
    }

    @RequestMapping("/deleteorder-control")
    public Result deleteorderControl(@RequestParam("orderid") int orderid, @RequestParam("orderstate") int orderstate) {
        boolean f = orderService.deleteOrder(orderid, orderstate, (Usr) session.getAttribute("admin"));
        if (f) {
            return new Result(SUCCESS_PAGE, "取消订单成功", true);
        } else {
            return new Result(ERROR_PAGE, "取消订单失败，订单已发货、已取消或无操作权限", false);
        }

    }

    @RequestMapping("/confirmorder-control")
    public Result confirmorderControl(@RequestParam("orderid") int orderid, @RequestParam("orderstate") int orderstate) {
        boolean f = orderService.confirmOrder(orderid, orderstate);
        if (f) {
            return new Result(SUCCESS_PAGE, "确认订单成功", true);

        } else {
            return new Result(ERROR_PAGE, "确认订单失败", false);
        }

    }
    @RequestMapping("/buyerhistoryconfirmorder-control")
    public Result buyerhistoryconfirmOrderControl(@RequestParam("orderid") int orderid, @RequestParam("orderstate") int orderstate) {
        return new Result(SUCCESS_PAGE, "确认订单成功", orderService.buyerhistoryconfirmOrder(orderid, orderstate));
    }



    @RequestMapping("/showbuyerorderinfo-control")
    public Result showbuyerorderinfoControl(@RequestParam("name") String name) {
        return new Result(SUCCESS_PAGE, "获得订单成功", orderService.showbuyerorderinfo(name));
    }


}
