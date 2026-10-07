package com.example.maoliang.Controller;
import com.example.maoliang.Common.extraOperations;

import com.example.maoliang.Controller.utils.Result;

import com.example.maoliang.Service.GoodService;
import com.example.maoliang.Entity.Good;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Service.OrderService;
import com.example.maoliang.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.maoliang.Controller.utils.Page.*;

@RestController
@RequestMapping( "/good/*")
@CrossOrigin
public class GoodController {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoodController.class);
    @Autowired
    private GoodService goodService;
    @Autowired
    private OrderService orderService;
    @Autowired
    public HttpSession session;

//    @Value("${upload.directory}")
//    private String uploadDirectory;


    @RequestMapping("/search-list-control")
    public Result searchList(@RequestBody Searchlistdata searchlistdata) {
        Usr currentUsr = (Usr) session.getAttribute("admin");
        String keyword=searchlistdata.getKeyword();
        String kind=searchlistdata.getKind();
        int ishistory = searchlistdata.getIshistory();

        if (null == currentUsr) {
            List<Good> searchList = goodService.showSearchGoods(keyword,kind,0,0,ishistory);
            session.setAttribute("sL", searchList);
            return new Result(BUYER_SEARCHLIST_PAGE,null, null);
        } else {
            int power = currentUsr.getPower();
            int userid = currentUsr.getUserid();
            List<Good> searchList = goodService.showSearchGoods(keyword,kind,power,userid,ishistory);
            session.setAttribute("sL", searchList);
            if(1==ishistory) {
                return new Result(SELLER_SEARCHHISTROYLIST_PAGE,null, null);
            }
            else {
                if(1==power) {
                    return new Result(SELLER_SEARCHLIST_PAGE,null, null);
                }
                else if(0==power){
                    return new Result(BUYER_SEARCHLIST_PAGE,null, null);
                }
            }
        }
        return null;
    }

    @RequestMapping("/search-list-nameonly-control")
    public Result searchListforName(@RequestBody Searchlistdata searchlistdata) {
        Usr currentUsr = (Usr) session.getAttribute("admin");
        String keyword=searchlistdata.getKeyword();
        String kind=searchlistdata.getKind();
        int ishistory = searchlistdata.getIshistory();

        List<String> results = null;

        if (null == currentUsr) {
            List<Good> searchList = goodService.showSearchGoods(keyword,kind,0,0,ishistory);
            results = new ArrayList<>();
            for (Good g : searchList) {
                results.add(g.getGoodname());
            }
            return new Result(BUYER_SEARCHLIST_PAGE,null, results);
        } else {
            int power = currentUsr.getPower();
            int userid = currentUsr.getUserid();
            List<Good> searchList = goodService.showSearchGoods(keyword,kind,power,userid,ishistory);
            results = new ArrayList<>();
            for (Good g : searchList) {
                results.add(g.getGoodname());
            }
            if(1==ishistory) {
                return new Result(SELLER_SEARCHHISTROYLIST_PAGE,null, results);
            }
            else {
                if(1==power) {
                    return new Result(SELLER_SEARCHLIST_PAGE,null, results);
                }
                else if(0==power){
                    return new Result(BUYER_SEARCHLIST_PAGE,null, results);
                }
            }
        }
        return null;
    }

    @RequestMapping("/buyer-all-good-list-control")
    public Result buyerAllGoodList() {
        List<Good> goodList = goodService.showNowGoods();
        return new Result(BUYER_ALL_GOODS_PAGE, null, goodList);
    }

    @RequestMapping("/seller-all-good-list-control")
    public Result sellerAllGoodList() {
        Usr currentUsr = (Usr) session.getAttribute("admin");
        int userid = currentUsr.getUserid();

        if (null != currentUsr) {
            List<Good> goodList = goodService.showAllGoods(userid);
            return new Result(SELLER_ALL_GOODS_PAGE, null, goodList);
        }
        return null;
    }

    @RequestMapping("/seller-all-historygood-list-control")
    public Result sellerAllHistoryGoodList() {
        Usr currentUsr = (Usr) session.getAttribute("admin");

        int userid = currentUsr.getUserid();

        if (null != currentUsr) {
            List<Good> goodList = goodService.showAllHistoryGoods(userid);
            return new Result(SELLER_ALL_HISTROYGOODS_PAGE, null, goodList);
        }
        return null;
    }

    @RequestMapping(value = "/upload-good")
    public Result uploadGood(@RequestParam("mediaFiles") MultipartFile[] fileParts,@ModelAttribute Uploadgoodsdata uploadgoodsdata) {
        Usr currentUsr = (Usr) session.getAttribute("admin");
        //System.out.println( uploadgoodsdata.getGoodname());
        String  goodname = uploadgoodsdata.getGoodname();
        String  description = uploadgoodsdata.getDescription();
        Double  calorie = uploadgoodsdata.getCalorie();
        String  catkind = uploadgoodsdata.getCatkind();
        String  catweight = uploadgoodsdata.getCatweight();
        String  catage = uploadgoodsdata.getCatage();
        Double price = uploadgoodsdata.getPrice();
        int state = 0;
        int number = uploadgoodsdata.getNumber();
        String kind = uploadgoodsdata.getKind();
        String subkind = uploadgoodsdata.getSubkind();
        int owner = currentUsr.getUserid();

        if (goodService.isUnique(goodname) != 0) { // 与数据库中商品名重复
            return new Result(ERROR_PAGE, "请勿上传重名商品！", SELLER_UPLOAD_GOOD_PAGE);
        }

        List<String> fileNames = new ArrayList<>(); // 用于存储所有文件的路径
        for (MultipartFile filePart : fileParts) {
            if (!filePart.isEmpty()) {
                String fileName = filePart.getOriginalFilename(); // 获取上传的文件名
                String extension = fileName.substring(fileName.lastIndexOf(".")); // 获取文件的扩展名
                String randomFileName = extraOperations.randomString(20) + extension; // 生成随机文件名

                // 设置上传路径
                String uploadPath =  "./img/" + randomFileName;
                File fileUploadDirectory = new File(uploadPath);

                try {
                    filePart.transferTo(fileUploadDirectory); // 保存文件
                    fileNames.add(uploadPath); // 本地测试！将文件路径添加到列表
                    //TODO:服务器模式的地址调用
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        String picture = String.join(",", fileNames);
        if(picture == null || "".equals(picture)) {
            picture="./img/food-1.png";//设置默认值
        }

        if (subkind == null || "".equals(subkind)) {
            subkind = "默认子类"; // 设置默认值
        }

        Good nowgood = new Good();
        nowgood.setGoodname(goodname);
        nowgood.setDescription(description);
        nowgood.setPrice(price);
        nowgood.setPicture(picture);
        nowgood.setState(state);
        nowgood.setNumber(number);
        nowgood.setKind(kind);
        nowgood.setSubkind(subkind);
        nowgood.setOwner(owner);
        nowgood.setCalorie(calorie);
        nowgood.setCatkind(catkind);
        nowgood.setCatweight(catweight);
        nowgood.setCatage(catage);

        goodService.add(nowgood);

        return new Result(SUCCESS_PAGE, "上传成功！", SELLER_ALL_GOODS_PAGE);
    }


    @RequestMapping("/upload-multiple-goods")
    public Result uploadMultipleGoods(@RequestBody UploadMultiplegoodsdata uploadMultiplegoodsdata) {
//        Usr currentUsr = (Usr) session.getAttribute("admin");
//        String path = "/src/main/resources/static/img/";
//        List<Good> uploadedGoods = new ArrayList<>();
        String[] goodnames=uploadMultiplegoodsdata.getGoodnames();
        Set<String> goodNameSet = new HashSet<>();

        System.out.println(goodnames);
        // 检查是否有重复的商品名称
        for (String goodname : goodnames) {
            if (!goodNameSet.add(goodname) || goodService.isUnique(goodname) != 0) {
                return new Result(ERROR_PAGE, "请勿上传重名商品：" + goodname + "！", SELLER_UPLOAD_MULTI_GOODS_PAGE);
            }
        }
        return new Result(SUCCESS_PAGE, null, null);
//        for (Uploadgoodsdata product : products) {
//            String goodname = product.getGoodname();
//            String description = product.getDescription();
//            int state = 0;
//            double price = product.getPrice();
//            int number = product.getNumber();
//            String kind = product.getKind();
//            String subkind = product.getSubkind();
//
//            int owner = currentUsr.getUserid();
//
//                // 过滤当前索引的文件数组
//                List<MultipartFile> files = new ArrayList<>();
//
//                for (MultipartFile mediaFile : fileParts) {
//                    String fileIndex = mediaFile.getName().substring(mediaFile.getName().indexOf('[') + 1, mediaFile.getName().indexOf(']'));
//                    if (fileIndex == goodname) {
//                        files.add(mediaFile);
//                    }
//                }
//
//                List<String> fileNames = new ArrayList<>();
//                for (MultipartFile filePart : files) {
//                    if (!filePart.isEmpty()) {
//                        String fileName = filePart.getOriginalFilename();
//                        String extension = fileName.substring(fileName.lastIndexOf("."));
//                        String randomFileName = extraOperations.randomString(20) + extension;
//
//                        String uploadPath = System.getProperty("user.dir") + path + randomFileName;
//                        File fileUploadDirectory = new File(uploadPath);
//
//                        try {
//                            filePart.transferTo(fileUploadDirectory);
//                            fileNames.add("/img/" + randomFileName);
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
//
//                String picture = String.join(",", fileNames);
//                if(picture == null || "".equals(picture)) {
//                    picture="./img/food-1.png";
//                }
//
//                if (subkind == null || "".equals(subkind)) {
//                    subkind = "默认子类";
//                }
//
//                Good newGood  = new Good();
//                newGood.setGoodname(goodname);
//                newGood.setDescription(description);
//                newGood.setPrice(price);
//                newGood.setPicture(picture);
//                newGood.setState(state);
//                newGood.setNumber(number);
//                newGood.setKind(kind);
//                newGood.setSubkind(subkind);
//                newGood.setOwner(owner);
//                goodService.add(newGood);
//                uploadedGoods.add(newGood);
//            }
//        return new Result(SUCCESS_PAGE, "上传成功！", SELLER_ALL_GOODS_PAGE);

    }

    @RequestMapping("/delete-good/{goodid}")
    public Result deleteGood(@PathVariable int goodid) {
        orderService.deletegood(goodid);//删除对应订单
        if ( goodService.remove(goodid) > 0) {

            return new Result(SUCCESS_PAGE, "商品删除成功！", SELLER_ALL_GOODS_PAGE);
        } else {
            // 商品不存在或删除失败
            return new Result(ERROR_PAGE, "商品不存在或删除失败！", SELLER_ALL_GOODS_PAGE);
        }
    }

    @RequestMapping("/buyer-show-good/{goodid}")
    public Result buyerShowGood(@PathVariable int goodid) {
        Good g = goodService.showGood(goodid);
        session.setAttribute("now-good", g);
        return new Result(BUYER_SHOP_PAGE, null, null);
    }

    @GetMapping("/details/{goodid}")
    public Good goodDetails(@PathVariable int goodid) {
        return goodService.showGood(goodid);
    }

    @RequestMapping("/modify-price")
    public Result modifyPriceforGood(@RequestBody Modifypricedata modifypricedata) {
        Usr currentUsr = (Usr) session.getAttribute("admin");
        double newPrice=modifypricedata.getNewPrice();
        int goodId=modifypricedata.getGoodId();

        Good g = goodService.showGood(goodId);

        if (null != currentUsr) {
            g.setPrice(newPrice);
            boolean result=goodService.updateGood(g);
            if(result){
                return new Result(SUCCESS_PAGE, "价格修改成功！", SELLER_ALL_GOODS_PAGE);
            }
            else{
                return new Result(ERROR_PAGE, "价格修改失败！", SELLER_ALL_GOODS_PAGE);
            }
        } else {
            return new Result(ERROR_PAGE, "请先登录！", LOGIN_PAGE);
        }
    }


}
