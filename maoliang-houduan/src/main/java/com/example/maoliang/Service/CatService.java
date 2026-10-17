package com.example.maoliang.Service;
import com.example.maoliang.Common.extraOperations;

import com.example.maoliang.Entity.Cat;
import com.example.maoliang.Repository.CatRepository;
import com.example.maoliang.Repository.GoodListRepository;
import com.example.maoliang.Repository.GoodRepository;
import com.example.maoliang.Entity.Good;
import com.example.maoliang.dto.GoodRecommendData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatService.class);

    @Autowired
    private CatRepository catRepository;

    public List<GoodRecommendData> showRecommend(int owner) {
        Cat cat = catRepository.findCatById(owner); // 获得猫咪信息
        if (cat == null) {
            return Collections.emptyList(); // 如果没有找到猫咪，返回空列表
        }

        // 根据猫咪的catweight，catage，catkind筛选商品
        List<Good> goodsByWeight = catRepository.findGoodsByCatWeight(cat.getCatweight());
        List<Good> goodsByAge = catRepository.findGoodsByCatAge(cat.getCatage());
        List<Good> goodsByKind = catRepository.findGoodsByCatKind(cat.getCatkind().trim());
        System.out.println(goodsByWeight);
        System.out.println(goodsByAge);
        System.out.println(goodsByKind);
        // 取交集，得到符合所有条件的商品列表
        List<Good> filteredGoods = new ArrayList<>(goodsByWeight); // 复制 goodsByWeight 列表

        // 取 goodsByWeight、goodsByAge 和 goodsByKind 的交集
        filteredGoods.retainAll(goodsByAge);
        filteredGoods.retainAll(goodsByKind);
        // The catalogue also contains treats and daily supplies; recommend only edible staple food.
        filteredGoods.removeIf(good -> !"猫咪主粮".equals(good.getKind())
                || good.getCalorie() <= 0 || !Double.isFinite(good.getCalorie())
                || good.getState() != 0 || good.getNumber() <= 0);

        // 如果商品超过三个，随机取出三个
        Collections.shuffle(filteredGoods);
        List<Good> recommendedGoods = filteredGoods.stream().limit(3).collect(Collectors.toList());

        // 根据猫的catstate计算DER
        double der = extraOperations.calculateDER(cat.getCatstate(),cat.getCatweight());

        // 计算每个商品的weight
        recommendedGoods.forEach(good -> {
            double weight = der / good.getCalorie();
            System.out.println(weight);
            System.out.println(good.getCalorie());
            good.setWeight(weight); // 假设Good类有一个setWeight方法来设置weight
        });

        // 返回新的goodlist
        return recommendedGoods.stream()
                .map(good -> new GoodRecommendData(good.getGoodid(), good.getGoodname(), good.getPrice(), good.getPicture(), good.getWeight()))
                .collect(Collectors.toList());
    }

    public List<Cat> getCatsByOwnerId(int ownerId) {
        return catRepository.findByOwner(ownerId);
    }
    public void addCat(int catid, String catname, String description, double catweight, int catstate, int catage, String catkind, int owner) {
        Cat cat = new Cat();
        cat.setCatid(catid);
        cat.setCatname(catname);
        cat.setDescription(description);
        cat.setCatweight(catweight);
        cat.setCatstate(catstate);
        cat.setCatage(catage);
        cat.setCatkind(catkind);
        cat.setOwner(owner);
        catRepository.save(cat);
    }
    //    public void updateCatInformation(int id, double catweight, int catstate, int catage) {
//        Optional<Cat> optionalCat = catRepository.findById(id);
//        if (optionalCat.isPresent()) {
//            Cat cat = optionalCat.get();
//            cat.setCatweight(catweight);
//            cat.setCatstate(catstate);
//            cat.setCatage(catage);
//            catRepository.save(cat);
//        } else {
//            throw new IllegalArgumentException("猫咪 ID 不存在");
//        }
//    }
    public void updateCatInformation(int id, double catweight, int catstate, int catage) {
        Cat cat = catRepository.findById(id);
        if (cat != null) {
            cat.setCatweight(catweight);
            cat.setCatstate(catstate);
            cat.setCatage(catage);
            catRepository.update(cat);
        } else {
            throw new RuntimeException("找不到对应ID的猫咪信息");
        }
    }

}
