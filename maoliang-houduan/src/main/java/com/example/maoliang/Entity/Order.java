package com.example.maoliang.Entity;
import com.example.maoliang.dto.OrderItem;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Transient;
import java.util.List;
import java.util.stream.Collectors;

@Entity
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int orderid;
	private String address;
	private String telephone;
	private String buyername;
	private String recipientname;
	private int goodid;
	private int number;
	private int orderstate;
	private int owner;
	@Transient
	private List<OrderItem> items = List.of();

	public int getNumber() {
		return number;
	}
	public void setNumber(int number) {
		this.number = number;
	}
	public int getOwner() {
		return owner;
	}
	public void setOwner(int owner) {
		this.owner = owner;
	}
	public int getOrderid() {
		return orderid;
	}
	public void setOrderid(int orderid) {
		this.orderid = orderid;
	}
	public String getAddress() {
		return address;
	}
	public void setAddress(String address) {
		this.address = address;
	}
	public String getTelephone() {
		return telephone;
	}
	public void setTelephone(String telephone) {
		this.telephone = telephone;
	}
	public String getBuyername() {
		return buyername;
	}
	public void setBuyername(String buyername) {
		this.buyername = buyername;
	}
	public String getRecipientname() {
		return recipientname == null ? buyername : recipientname;
	}
	public void setRecipientname(String recipientname) {
		this.recipientname = recipientname;
	}
	public int getGoodid() {
		return goodid;
	}
	public void setGoodid(int goodid) {
		this.goodid = goodid;
	}
	public int getOrderstate() {
		return orderstate;
	}
	public void setOrderstate(int orderstate) {
		this.orderstate = orderstate;
	}
	public List<OrderItem> getItems() {
		return items;
	}
	public void setItems(List<OrderItem> items) {
		this.items = items == null ? List.of() : List.copyOf(items);
	}
	public String getGoodIdsSummary() {
		if (items == null || items.isEmpty()) return String.valueOf(goodid);
		return items.stream().map(item -> String.valueOf(item.goodid())).collect(Collectors.joining(", "));
	}
	public String getGoodsSummary() {
		if (items == null || items.isEmpty()) return "商品#" + goodid + " ×" + number;
		return items.stream()
				.map(item -> (item.goodname() == null || item.goodname().isBlank() ? "商品#" + item.goodid() : item.goodname().trim()) + " ×" + item.number())
				.collect(Collectors.joining("、"));
	}
}

