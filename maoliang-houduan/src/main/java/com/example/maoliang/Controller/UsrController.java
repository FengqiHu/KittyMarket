package com.example.maoliang.Controller;


import com.example.maoliang.Controller.utils.Result;
import com.example.maoliang.Entity.Good;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Service.GoodService;
import com.example.maoliang.Service.UsrService;
import com.example.maoliang.dto.*;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

import static com.example.maoliang.Controller.utils.Page.*;

@RestController
@RequestMapping( "/usr/*")
public class UsrController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UsrController.class);
    @Autowired
    private UsrService usrService;
    @Autowired
    private GoodService goodService;
    @Autowired
    public HttpSession session;//存储session数据

    @RequestMapping("/login-control")
    public Result LoginControl(@RequestBody Logindata login//RequestBody用于传输数据
                               //Logindata自命,需要从前端传入的【所有】数据
    ) {
        String username = login.getUsername();
        String pwd = login.getPassword();
        try {
            Usr usr = usrService.search(username);
            if (usr != null && pwd.equals(usr.getPwd().trim())) {//数据库导出字符串有后置空格
                System.out.println(usr.getPower());
                session.setAttribute("admin", usr);

                //System.out.println(session.getAttribute("admin"));
                if (usr.getPower() == 0) {
                    // 买家权限，进入商品首页
                    return new Result(BUYER_PAGE, "success", null);
                } else if (usr.getPower() == 1) {
                    // 卖家权限，进入后台管理all商品页面
                    List<Good> gList = goodService.showAllGoods(usr.getUserid());
                    return new Result(SELLER_PAGE, "success", gList);
                } else {
                    Errordata ed = new Errordata();
                    // 处理其他权限或错误情况
                    return new Result(ERROR_PAGE, "未知权限", "login");
                }
            } else {
                return new Result(ERROR_PAGE, "用户名错误或密码错误", "login");
            }
        } catch (SQLException e) {
            LOGGER.error("Error handling login", e);
            return new Result(ERROR_PAGE, "登录出错，请稍后重试", "login");
        }
    }

    @RequestMapping( "/register-control")
    public Result RegisterControl(@RequestBody RegisterData register) {
        String username = register.getUsername();
        try {
            if ((register.getUsername()==null || register.getPassword()==null || register.getAnswer()==null ||register.getQuestion()==null)){
                LOGGER.error("Empty error");
                return new Result(RENAME_PAGE,"错误，请补充对应信息！","register");
            } else if (register.getPower() == 1 && (register.getPhone()==null || register.getAddress()==null )) {
                LOGGER.error("Empty error");
                return new Result(RENAME_PAGE,"错误，请补充对应信息！","register");
            }
            if (register.getUsername().length() > 10 || register.getQuestion().length() > 50){
                LOGGER.error("Too long error");
                return new Result(RENAME_PAGE,"错误，用户名或密保问题过长！","register");
            } else if (register.getPhone().length()!=11){
                LOGGER.error("Wrong phone number");
                return new Result(RENAME_PAGE,"错误，请输入正确电话号码！","register");
            } else if(usrService.searchName(username) == false) {
                Usr newUsr = new Usr();
                newUsr.setUsername(username);
                newUsr.setPwd(register.getPassword());
                newUsr.setQuestion(register.getQuestion());
                newUsr.setAnswer(register.getAnswer());
                newUsr.setPower(register.getPower());
                //买家与卖家不同设定
                if (register.getPower() == 0) {
                    newUsr.setAddress(register.getAddress());
                    newUsr.setPhone(register.getPhone());
                } else {
                    newUsr.setAddress(null);
                    newUsr.setPhone(null);
                }
                //注册
                usrService.register(newUsr);
                return new Result(LOGIN_PAGE, "success", username);
            } else {
                LOGGER.error("Error handling register");
                return new Result(RENAME_PAGE,"错误，用户名重复！","register");
            }
        } catch (SQLException e) {
            LOGGER.error("Error handling register", e);
            return new Result(ERROR_PAGE,"系统错误","register");
        }
    }

    @RequestMapping( "/changedpwd-control")
    public Result changedpwd(@RequestBody Changedpwddata changedpwddata) throws SQLException {
        Usr usr = (Usr) session.getAttribute("admin");
        String userpwd=usr.getPwd().trim();
        String oldpwd=changedpwddata.getOldpwd();
        String newpwd=changedpwddata.getNewpwd();
        String newpwd1=changedpwddata.getNewpwd1();
        String username=((Usr)session.getAttribute("admin")).getUsername();
        if (!userpwd.equals(oldpwd)) {
            return new Result(ERROR_PAGE,"旧密码错误",UPDATEPWD_PAGE);
        } else if (oldpwd.equals(newpwd)) {
            return new Result(ERROR_PAGE,"新密码与旧密码一致",UPDATEPWD_PAGE);
        } else if (!newpwd.equals(newpwd1)) {
            return new Result(ERROR_PAGE,"新密码与确认密码不一致",UPDATEPWD_PAGE);
        } else {
            usrService.modifyPwd(username,newpwd);
            return new Result(SELLER_PAGE,"密码修改成功！",null);
        }
    }

    @RequestMapping("/all-buyer")
    public Result allBuyerControl() throws SQLException {
        List<Usr> buyerList = usrService.loadBuyer();
        if(buyerList==null){
            return new Result("null","noBuyer",null);
        }else{
            return new Result(BUYER_PAGE,"success",buyerList);
        }

    }
    @RequestMapping( "/forgetPwd-control")
    public Result ForgetControl(@RequestBody ForgetPwdData name) {
        String username = name.getUsername();
        try {
            //如果找到用户名
            if (usrService.searchName(username)){
                Usr usr = new Usr();
                usr = usrService.search(username);
                session.setAttribute("forgetUsr",usr);
                return new Result(ANSWER_PAGE,"answer", usr);
            }else{
                LOGGER.error("Wrong Name!");
                return new Result(ERROR_PAGE,"noName","Wrong Name");
            }
        } catch (SQLException e) {
            LOGGER.error("Error handling find Password", e);
            return new Result(ERROR_PAGE,"error","findPwd");
        }
    }
    @RequestMapping( "/answer-control")
    public Result AnswerControl(@RequestBody AnswerData answerData) {
        String answer = answerData.getAnswer();
        Usr usr = (Usr)session.getAttribute("forgetUsr");
        if (usr.getAnswer().equals(answer)){
            LOGGER.info("Right answer");
            session.removeAttribute("forgetUsr");
            return new Result("right","right answer", "right answer");
        }else{
            LOGGER.error("Wrong Answer!");
            return new Result(ERROR_PAGE,"BadAnswer","Wrong Answer");
        }
    }

}
