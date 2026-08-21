package com.jquick.asm.bench;

/**
 * 基准测试目标 POJO：模拟典型业务对象（用户信息）。
 *
 * <p>字段类型覆盖：基本类型、包装类型、字符串，便于对比各转换器性能。
 */
public class BenchmarkUser {

    private long id;
    private String name;
    private int age;
    private String email;
    private boolean active;
    private double score;
    private String address;

    public BenchmarkUser() {
    }

    public BenchmarkUser(long id, String name, int age, String email,
                         boolean active, double score, String address) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.email = email;
        this.active = active;
        this.score = score;
        this.address = address;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "BenchmarkUser{id=" + id + ", name='" + name + '\'' + ", age=" + age
                + ", email='" + email + '\'' + ", active=" + active
                + ", score=" + score + ", address='" + address + '\'' + '}';
    }
}
