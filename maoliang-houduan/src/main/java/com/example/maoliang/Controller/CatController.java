package com.example.maoliang.Controller;

import com.example.maoliang.Controller.utils.Result;
import com.example.maoliang.Entity.Cat;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Service.CatService;
import com.example.maoliang.Service.CatalogRagService;
import com.example.maoliang.dto.CatRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cat")
public class CatController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatController.class);

    @Autowired
    private CatService catService;

    @Autowired
    private CatalogRagService catalogRagService;

    @Autowired
    private HttpSession session;

    private Usr currentUser() {
        return (Usr) session.getAttribute("admin");
    }

    @GetMapping("/show-cat")
    public Result showCats() {
        Usr user = currentUser();
        if (user == null) return new Result("error", "请先登录", null);
        try {
            return new Result("success", "获取猫咪信息成功", catService.getCatsByOwnerId(user.getUserid()));
        } catch (Exception e) {
            LOGGER.error("获取猫咪信息出错", e);
            return new Result("error", "猫咪档案加载失败", null);
        }
    }

    @GetMapping("/show-recommend/{id}")
    public Result showRecommend(@PathVariable int id) {
        Usr user = currentUser();
        if (user == null) return new Result("error", "请先登录", null);
        if (user.getUserid() != id) return new Result("error", "无权查看该推荐", null);
        try {
            Cat cat = catService.getCatsByOwnerId(id).stream().findFirst().orElse(null);
            return new Result(null, null, cat == null ? java.util.List.of() : catalogRagService.recommend(cat).items());
        } catch (Exception e) {
            LOGGER.error("生成猫咪推荐失败", e);
            return new Result("error", "推荐生成失败，请稍后重试", null);
        }
    }

    @GetMapping("/recommend/{catId}")
    public Result recommendForCat(@PathVariable int catId) {
        Usr user = currentUser();
        if (user == null) return new Result("error", "请先登录", null);
        try {
            Cat cat = catService.getOwnedCat(catId, user.getUserid());
            if (cat == null) return new Result("error", "猫咪档案不存在或无权查看", null);
            return new Result("success", "推荐已生成", catalogRagService.recommend(cat));
        } catch (Exception e) {
            LOGGER.error("生成猫咪推荐失败", e);
            return new Result("error", "推荐生成失败，请稍后重试", null);
        }
    }

    @PostMapping("/add-cat")
    public Result addCat(@RequestBody CatRequest request) {
        Usr user = currentUser();
        if (user == null) return new Result("error", "请先登录", null);
        String validationError = validate(request);
        if (validationError != null) return new Result("error", validationError, null);
        try {
            catService.addCat(toCat(request), user.getUserid());
            return new Result("success", "猫咪档案已添加", null);
        } catch (Exception e) {
            LOGGER.error("添加猫咪信息出错", e);
            return new Result("error", "添加失败，请稍后重试", null);
        }
    }

    @PutMapping("/update-cat/{id}")
    public Result updateCat(@PathVariable int id, @RequestBody CatRequest request) {
        Usr user = currentUser();
        if (user == null) return new Result("error", "请先登录", null);
        String validationError = validate(request);
        if (validationError != null) return new Result("error", validationError, null);
        try {
            if (!catService.updateCat(id, toCat(request), user.getUserid())) {
                return new Result("error", "猫咪档案不存在或无权编辑", null);
            }
            return new Result("success", "猫咪档案已更新", null);
        } catch (Exception e) {
            LOGGER.error("编辑猫咪信息出错", e);
            return new Result("error", "保存失败，请稍后重试", null);
        }
    }

    @DeleteMapping("/delete-cat/{id}")
    public Result deleteCat(@PathVariable int id) {
        Usr user = currentUser();
        if (user == null) return new Result("error", "请先登录", null);
        try {
            if (!catService.deleteCat(id, user.getUserid())) {
                return new Result("error", "猫咪档案不存在或无权删除", null);
            }
            return new Result("success", "猫咪档案已删除", null);
        } catch (Exception e) {
            LOGGER.error("删除猫咪信息出错", e);
            return new Result("error", "删除失败，请稍后重试", null);
        }
    }

    private String validate(CatRequest request) {
        if (request == null || request.getCatname() == null || request.getCatname().isBlank()
                || request.getCatname().trim().length() > 20) return "猫咪名字请输入 1 至 20 个字符";
        if (request.getCatkind() == null || request.getCatkind().isBlank()
                || request.getCatkind().trim().length() > 20) return "品种请输入 1 至 20 个字符";
        if (request.getDescription() != null && request.getDescription().trim().length() > 100)
            return "简介不能超过 100 个字符";
        if (request.getCatage() < 0 || request.getCatage() > 40) return "年龄请输入 0 至 40 岁";
        if (!Double.isFinite(request.getCatweight()) || request.getCatweight() <= 0 || request.getCatweight() > 100)
            return "体重请输入 0 至 100 kg 之间的数字";
        if (request.getCatstate() < 1 || request.getCatstate() > 7) return "请选择生长状态";
        return null;
    }

    private Cat toCat(CatRequest request) {
        Cat cat = new Cat();
        cat.setCatname(request.getCatname().trim());
        cat.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        cat.setCatkind(request.getCatkind().trim());
        cat.setCatage(request.getCatage());
        cat.setCatweight(request.getCatweight());
        cat.setCatstate(request.getCatstate());
        return cat;
    }
}
