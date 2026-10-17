-- Complete demo seed: accounts, 26 products, image paths, history, an order and cats.
-- Select the target database and run schema.sql first. Safe to import repeatedly.
-- Existing passwords, prices, inventory and orders are preserved; demo image paths are refreshed.
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO MLuser(username,pwd,power,question,answer)
SELECT '123','123',1,'who are you?','Xiaoming'
WHERE NOT EXISTS (SELECT 1 FROM MLuser WHERE username = '123');
INSERT INTO MLuser(username,pwd,power,question,answer)
SELECT '111','111',0,'who are you?','Xiaoming'
WHERE NOT EXISTS (SELECT 1 FROM MLuser WHERE username = '111');
SET @demo_seller = (SELECT MIN(userid) FROM MLuser WHERE username = '123' AND power = 1);
SET @demo_buyer = (SELECT MIN(userid) FROM MLuser WHERE username = '111' AND power = 0);

INSERT INTO MLinfo(userid,phone,address)
SELECT @demo_buyer,'15911111234','浙江工商大学'
WHERE NOT EXISTS (SELECT 1 FROM MLinfo WHERE userid = @demo_buyer);

DROP TEMPORARY TABLE IF EXISTS kitty_demo_products;
CREATE TEMPORARY TABLE kitty_demo_products AS
SELECT goodname,description,price,picture,state,number,kind,subkind,owner,calorie,catkind,catage,catweight
FROM MLgood WHERE 1 = 0;
INSERT INTO kitty_demo_products VALUES
('猫干粮','美味猫干粮',13.9,'/demo/products/dry-cat-food.webp',0,12,'猫咪主粮','猫干粮',@demo_seller,3.4,'波斯猫','1-10','1-7'),
('猫湿粮','美味猫湿粮',1.0,'/demo/products/wet-cat-food.webp',0,3,'猫咪主粮','猫湿粮',@demo_seller,2.1,'波斯猫,布偶猫','1-10','1-7'),
('鲜鸡肉全价成猫粮','鲜鸡肉配方，颗粒酥脆，2kg 日常装。',89,'/demo/products/chicken-adult-kibble.webp',0,120,'猫咪主粮','成猫干粮',@demo_seller,3.8,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-10','1-12'),
('三文鱼亮毛猫粮','三文鱼与鱼油搭配，1.5kg 装。',109,'/demo/products/salmon-coat-kibble.webp',0,86,'猫咪主粮','成猫干粮',@demo_seller,3.9,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-10','1-12'),
('幼猫成长鸡肉粮','细小颗粒，适合幼猫咀嚼，1kg 装。',69,'/demo/products/kitten-chicken-kibble.webp',0,95,'猫咪主粮','幼猫粮',@demo_seller,4.1,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','0-1','1-12'),
('室内成猫火鸡粮','火鸡肉风味，室内猫日常主食，2kg 装。',99,'/demo/products/indoor-turkey-kibble.webp',0,72,'猫咪主粮','成猫干粮',@demo_seller,3.6,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-10','1-12'),
('高龄猫软颗粒粮','易咀嚼软颗粒，500g 小袋装。',59,'/demo/products/senior-soft-kibble.webp',0,64,'猫咪主粮','老年猫粮',@demo_seller,3.5,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','7-20','1-12'),
('鸡肉南瓜主食罐','鸡肉与南瓜搭配，85g 单罐装。',12.9,'/demo/products/chicken-pumpkin-can.webp',0,240,'猫咪主粮','主食罐头',@demo_seller,1.1,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-15','1-12'),
('金枪鱼主食罐','细腻鱼肉口感，85g 单罐装。',14.9,'/demo/products/tuna-meal-can.webp',0,180,'猫咪主粮','主食罐头',@demo_seller,1.2,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-15','1-12'),
('鸭肉鲜食餐包','鸭肉鲜食风味，70g 便携餐包。',9.9,'/demo/products/duck-meal-pouch.webp',0,160,'猫咪主粮','鲜食餐包',@demo_seller,1.0,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-15','1-12'),
('冻干鸡胸肉粒','单一鸡肉原料，40g 零食装。',29.9,'/demo/products/freeze-dried-chicken.webp',0,150,'猫咪零食','冻干零食',@demo_seller,4.5,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-15','1-12'),
('冻干鹌鹑蛋黄','酥松蛋黄小粒，30g 装，少量奖励。',25.9,'/demo/products/freeze-dried-quail-yolk.webp',0,100,'猫咪零食','冻干零食',@demo_seller,5.1,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-15','1-12'),
('三文鱼冻干块','小块鱼肉冻干，35g 装。',35.9,'/demo/products/freeze-dried-salmon.webp',0,88,'猫咪零食','冻干零食',@demo_seller,4.7,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-15','1-12'),
('鸡肉慕斯猫条','细腻慕斯质地，12g × 6 支。',19.9,'/demo/products/chicken-mousse-tubes.webp',0,220,'猫咪零食','猫条',@demo_seller,0.8,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-15','1-12'),
('金枪鱼鲜肉猫条','鱼肉风味奖励零食，12g × 6 支。',21.9,'/demo/products/tuna-treat-tubes.webp',0,210,'猫咪零食','猫条',@demo_seller,0.9,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-15','1-12'),
('磨牙鸡肉小饼干','鸡肉风味小饼干，60g 装。',16.9,'/demo/products/chicken-cat-biscuits.webp',0,140,'猫咪零食','饼干',@demo_seller,3.8,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-15','1-12'),
('猫草种植小套装','种子、基质与种植盒组合，按说明培育。',15.9,'/demo/products/cat-grass-kit.webp',0,75,'猫咪零食','猫草',@demo_seller,0.2,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-15','1-12'),
('鲜虾鸡肉奖励粒','鸡肉与鲜虾风味，45g 训练奖励装。',27.9,'/demo/products/shrimp-chicken-rewards.webp',0,96,'猫咪零食','肉粒',@demo_seller,4.0,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','1-15','1-12'),
('原味豆腐猫砂','低粉尘豆腐猫砂，6L 袋装。',32.9,'/demo/products/tofu-cat-litter.webp',0,180,'猫咪日用','猫砂',@demo_seller,0,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','0-20','1-12'),
('混合除味猫砂','豆腐砂与矿砂混合，6L 袋装。',36.9,'/demo/products/mixed-cat-litter.webp',0,155,'猫咪日用','猫砂',@demo_seller,0,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','0-20','1-12'),
('开放式加高猫砂盆','加高围边，附猫砂铲，易拆洗。',49.9,'/demo/products/high-sided-litter-box.webp',0,56,'猫咪日用','猫砂盆',@demo_seller,0,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','0-20','1-12'),
('陶瓷斜口猫碗','陶瓷材质，斜口设计，容量约 300ml。',39.9,'/demo/products/tilted-ceramic-bowl.webp',0,90,'猫咪日用','食具',@demo_seller,0,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','0-20','1-12'),
('不锈钢双碗套装','双碗可拆卸，底座防滑，便于清洗。',45.9,'/demo/products/stainless-double-bowls.webp',0,68,'猫咪日用','食具',@demo_seller,0,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','0-20','1-12'),
('羽毛逗猫棒','可替换羽毛头，适合陪伴互动。',18.9,'/demo/products/feather-cat-wand.webp',0,130,'猫咪日用','玩具',@demo_seller,0,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','0-20','1-12'),
('瓦楞纸猫抓板','双面可用瓦楞纸抓板，约 45cm。',24.9,'/demo/products/corrugated-cat-scratcher.webp',0,115,'猫咪日用','猫抓板',@demo_seller,0,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','0-20','1-12'),
('柔软猫咪睡垫','可清洗柔软睡垫，约 50 × 40cm。',59.9,'/demo/products/soft-cat-sleep-mat.webp',0,60,'猫咪日用','猫窝睡垫',@demo_seller,0,'波斯猫,布偶猫,英国短毛猫,美国短毛猫,中华田园猫','0-20','1-12');

INSERT INTO MLgood(goodname,description,price,picture,state,number,kind,subkind,owner,calorie,catkind,catage,catweight)
SELECT d.* FROM kitty_demo_products d
WHERE NOT EXISTS (SELECT 1 FROM MLgood g WHERE g.goodname = d.goodname AND g.owner = d.owner);

-- Refresh only the demo catalogue's image paths, including databases with the older illustrations.
UPDATE MLgood g JOIN kitty_demo_products d ON d.goodname = g.goodname AND d.owner = g.owner
SET g.picture = d.picture;

-- Preserve the two original historical snapshots on a newly initialized database.
INSERT INTO MLhistorygood(goodid,goodname,description,price,picture,number,kind,subkind,createdate,owner,calorie,catkind,catage,catweight)
SELECT g.goodid,g.goodname,g.description,
       CASE WHEN g.goodname = '猫湿粮' THEN 9.9 ELSE g.price END,
       g.picture,1,CASE WHEN g.goodname = '猫湿粮' THEN '猫湿粮' ELSE g.kind END,g.subkind,
       CASE WHEN g.goodname = '猫湿粮' THEN '2023-11-11 13:13:14' ELSE '2023-11-11 13:13:13' END,
       g.owner,g.calorie,g.catkind,g.catage,g.catweight
FROM MLgood g
WHERE g.owner = @demo_seller AND g.goodname IN ('猫干粮','猫湿粮')
  AND NOT EXISTS (SELECT 1 FROM MLhistorygood h WHERE h.goodid = g.goodid);

INSERT INTO MLhistorygood(goodid,goodname,description,price,picture,number,kind,subkind,createdate,owner,calorie,catkind,catage,catweight)
SELECT g.goodid,g.goodname,g.description,g.price,g.picture,g.number,g.kind,g.subkind,NOW(),g.owner,g.calorie,g.catkind,g.catage,g.catweight
FROM MLgood g JOIN kitty_demo_products d ON d.goodname = g.goodname AND d.owner = g.owner
WHERE NOT EXISTS (SELECT 1 FROM MLhistorygood h WHERE h.goodid = g.goodid);
UPDATE MLhistorygood h JOIN kitty_demo_products d ON d.goodname = h.goodname AND d.owner = h.owner
SET h.picture = d.picture;

-- Resolve foreign keys by the seeded records instead of assuming IDs start at 1.
SET @demo_dry_food = (SELECT MIN(goodid) FROM MLgood WHERE owner = @demo_seller AND goodname = '猫干粮');
INSERT INTO MLorder(address,telephone,buyername,goodid,number,orderstate,owner)
SELECT '浙江工商大学','15911111234','张三',@demo_dry_food,1,1,@demo_seller
WHERE NOT EXISTS (SELECT 1 FROM MLorder WHERE goodid = @demo_dry_food AND buyername = '张三' AND owner = @demo_seller);
INSERT INTO MLcat(catname,description,catweight,catstate,catage,catkind,owner)
SELECT '嘟嘟可','可爱的白色布偶猫',1.2,3,1,'布偶猫',@demo_seller
WHERE NOT EXISTS (SELECT 1 FROM MLcat WHERE owner = @demo_seller AND catname = '嘟嘟可');
INSERT INTO MLcat(catname,description,catweight,catstate,catage,catkind,owner)
SELECT '蹦蹦','可爱的白色波斯猫',1.2,4,1,'波斯猫',@demo_buyer
WHERE NOT EXISTS (SELECT 1 FROM MLcat WHERE owner = @demo_buyer AND catname = '蹦蹦');

DROP TEMPORARY TABLE kitty_demo_products;
COMMIT;
