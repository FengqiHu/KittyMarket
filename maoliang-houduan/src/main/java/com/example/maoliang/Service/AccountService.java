package com.example.maoliang.Service;

import com.example.maoliang.Entity.Usr;
import com.example.maoliang.dto.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Statement;
import java.util.List;

@Service
public class AccountService {
    private final JdbcTemplate jdbc;
    public AccountService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Usr lockUser(int userId) { return readUser(userId, true); }

    private Usr readUser(int userId, boolean lock) {
        var users = jdbc.query("SELECT * FROM MLuser WHERE userid = ?" + (lock ? " FOR UPDATE" : ""),
                BeanPropertyRowMapper.newInstance(Usr.class), userId);
        if (users.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
        return users.get(0);
    }

    public Usr current(int userId) {
        Usr user = readUser(userId, false);
        var addresses = jdbc.queryForList("SELECT telephone AS phone, address FROM MLaddress WHERE user_id = ? AND is_default = TRUE LIMIT 1", userId);
        if (addresses.isEmpty() && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT address_initialized FROM MLuser WHERE userid = ?", Boolean.class, userId))) {
            addresses = jdbc.queryForList("SELECT phone, address FROM MLinfo WHERE userid = ? LIMIT 1", userId);
        }
        if (!addresses.isEmpty()) {
            user.setPhone((String) addresses.get(0).get("phone"));
            user.setAddress((String) addresses.get(0).get("address"));
        }
        return user;
    }

    @Transactional
    public Usr rename(int userId, AccountNameData request) {
        String name = text(request == null ? null : request.username(), 10, "账号名称");
        Usr user = lockUser(userId);
        try {
            jdbc.update("UPDATE MLuser SET username = ? WHERE userid = ?", name, userId);
        } catch (DuplicateKeyException exception) { throw new IllegalArgumentException("这个账号名称已被使用"); }
        // Legacy orders identify their buyer by account name; keep them attached after a rename.
        jdbc.update("UPDATE MLorder SET buyername = ? WHERE buyername = ?", name, user.getUsername());
        return current(userId);
    }

    @Transactional
    public void changePassword(int userId, AccountPasswordData request) {
        Usr user = lockUser(userId);
        if (request == null || !AccountPasswords.matches(request.currentPassword(), user.getPwd())) throw new IllegalArgumentException("当前密码不正确");
        String password = request.newPassword();
        if (password == null || password.length() < 8 || password.length() > 64 || password.isBlank()) throw new IllegalArgumentException("新密码需要为 8–64 个字符");
        if (!password.equals(request.confirmPassword())) throw new IllegalArgumentException("两次输入的新密码不一致");
        if (AccountPasswords.matches(password, user.getPwd())) throw new IllegalArgumentException("新密码不能与当前密码相同");
        jdbc.update("UPDATE MLuser SET pwd = ? WHERE userid = ?", AccountPasswords.encode(password), userId);
    }

    // The account row serializes address-book edits and initialization for this user.
    private void initializeAddresses(Usr user) {
        int userId = user.getUserid();
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT address_initialized FROM MLuser WHERE userid = ?", Boolean.class, userId))) return;
        var legacy = jdbc.queryForList("SELECT phone, address FROM MLinfo WHERE userid = ? LIMIT 1", userId);
        if (!legacy.isEmpty()) {
            String phone = String.valueOf(legacy.get(0).get("phone")).trim();
            String address = String.valueOf(legacy.get(0).get("address")).trim();
            if (phone.matches("[0-9]{11}") && !address.isBlank() && address.length() <= 99) {
                jdbc.update("INSERT INTO MLaddress(user_id,recipient,telephone,address,is_default) VALUES (?,?,?,?,TRUE)", userId, user.getUsername().trim(), phone, address);
            }
        }
        jdbc.update("UPDATE MLuser SET address_initialized = TRUE WHERE userid = ?", userId);
    }

    @Transactional
    public List<ShippingAddress> addresses(int userId) {
        initializeAddresses(lockUser(userId));
        return queryAddresses(userId);
    }

    private List<ShippingAddress> queryAddresses(int userId) {
        return jdbc.query("SELECT * FROM MLaddress WHERE user_id = ? ORDER BY is_default DESC, id", (rs, row) ->
                new ShippingAddress(rs.getLong("id"), rs.getString("recipient"), rs.getString("telephone"), rs.getString("address"), rs.getBoolean("is_default")), userId);
    }

    @Transactional
    public ShippingAddress saveAddress(int userId, Long id, AddressData request) {
        if (request == null) throw new IllegalArgumentException("请填写收货地址");
        String recipient = text(request.recipient(), 10, "收货人");
        String phone = request.telephone() == null ? "" : request.telephone().trim();
        if (!phone.matches("[0-9]{11}")) throw new IllegalArgumentException("联系电话需要是 11 位数字");
        String address = text(request.address(), 99, "详细地址");
        initializeAddresses(lockUser(userId));
        if (id != null) ownedAddress(userId, id);
        if (Boolean.TRUE.equals(request.isDefault())) jdbc.update("UPDATE MLaddress SET is_default = FALSE WHERE user_id = ?", userId);
        if (id == null) {
            var key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("INSERT INTO MLaddress(user_id,recipient,telephone,address,is_default) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setInt(1, userId); statement.setString(2, recipient); statement.setString(3, phone);
                statement.setString(4, address); statement.setBoolean(5, Boolean.TRUE.equals(request.isDefault()));
                return statement;
            }, key);
            id = key.getKey().longValue();
        } else {
            jdbc.update("UPDATE MLaddress SET recipient = ?, telephone = ?, address = ?, is_default = ? WHERE id = ? AND user_id = ?",
                    recipient, phone, address, Boolean.TRUE.equals(request.isDefault()), id, userId);
        }
        ensureDefault(userId);
        return ownedAddress(userId, id);
    }

    @Transactional
    public void deleteAddress(int userId, long id) {
        initializeAddresses(lockUser(userId));
        ownedAddress(userId, id);
        jdbc.update("DELETE FROM MLaddress WHERE id = ? AND user_id = ?", id, userId);
        ensureDefault(userId);
    }

    @Transactional
    public void defaultAddress(int userId, long id) {
        initializeAddresses(lockUser(userId));
        ownedAddress(userId, id);
        jdbc.update("UPDATE MLaddress SET is_default = (id = ?) WHERE user_id = ?", id, userId);
    }

    private ShippingAddress ownedAddress(int userId, long id) {
        return queryAddresses(userId).stream().filter(address -> address.id() == id).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在或无权操作"));
    }

    private void ensureDefault(int userId) {
        var addresses = queryAddresses(userId);
        if (!addresses.isEmpty() && addresses.stream().noneMatch(ShippingAddress::isDefault))
            jdbc.update("UPDATE MLaddress SET is_default = TRUE WHERE id = ? AND user_id = ?", addresses.get(0).id(), userId);
    }

    private String text(String value, int max, String label) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > max) throw new IllegalArgumentException(label + "不能为空且不能超过 " + max + " 个字符");
        return text;
    }
}
