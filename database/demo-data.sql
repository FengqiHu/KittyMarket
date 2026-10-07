-- Original project demo data. Import once into a newly created, empty database.
USE maoliang;
START TRANSACTION;
INSERT INTO MLuser VALUES (1, '123', '123', 1,'who are you?','Xiaoming');
INSERT INTO MLuser VALUES (2, '111', '111', 0,'who are you?','Xiaoming');
INSERT INTO MLinfo VALUES (2, '15911111234','浙江工商大学');
INSERT INTO MLgood(goodname, description, price, picture, state, number, kind, subkind, owner,calorie,catkind,catweight,catage)  VALUES ('猫干粮', '美味猫干粮', 13.9,'./assets/img/buyer/food-1.jpg',0,12,'猫咪主粮','猫干粮',1,3.4,'波斯猫','1-7','1-10');
INSERT INTO MLgood(goodname, description, price, picture, state, number, kind, subkind, owner,calorie,catkind,catweight,catage)  VALUES ('猫湿粮', '美味猫湿粮', 1.0,'./assets/img/buyer/food-2.jpg',0,3,'猫咪主粮','猫湿粮',1,2.1,'波斯猫,布偶猫','1-7','1-10');
INSERT INTO MLhistorygood VALUES (1, '猫干粮', '美味猫干粮', 13.9,'./assets/img/buyer/food-1.jpg',1,'猫咪主粮','猫干粮','2023-11-11 13:13:13',1,3.4,'波斯猫','1-7','1-10');
INSERT INTO MLhistorygood VALUES (2, '猫湿粮', '美味猫湿粮', 9.9,'./assets/img/buyer/food-2.jpg',1,'猫湿粮','猫湿粮','2023-11-11 13:13:14',1,2.1,'波斯猫,布偶猫','1-7','1-10');
INSERT INTO MLorder VALUES (1, '浙江工商大学','15911111234', '张三',1, 1,1,1);
INSERT INTO MLcat VALUES (1, '嘟嘟可','可爱的白色布偶猫', 1.2,3, 1,'布偶猫',1);
INSERT INTO MLcat VALUES (2, '蹦蹦','可爱的白色波斯猫', 1.2,4, 1,'波斯猫',2);
COMMIT;
