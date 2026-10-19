package com.example.maoliang.Service;

import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Repository.UsrListRepository;
import com.example.maoliang.Repository.UsrRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
public class UsrService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UsrService.class);


    @Autowired
    private UsrRepository usrRepository;

    @Autowired
    UsrListRepository usrListRepository;

    public Usr search(String username) throws SQLException {
        Usr usr=usrRepository.search(username);
//        System.out.println(usr.getUserid());
        return usr;
    }

    public void modifyPwd(String username,String password) throws SQLException {
        usrRepository.modifyPwd(username,password);
//        System.out.println(usr.getUserid());
    }

    public List<Usr> loadBuyer() throws  SQLException {
        return usrListRepository.loadBuyer();
    }

    public boolean searchName(String username) throws SQLException {
        return usrRepository.searchName(username);
    }

    public void register(Usr usr) throws SQLException {
        usr.setPwd(AccountPasswords.encode(usr.getPwd()));
        usrRepository.register(usr);
    }

}
