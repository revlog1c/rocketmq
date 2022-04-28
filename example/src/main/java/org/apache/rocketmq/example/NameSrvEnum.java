package org.apache.rocketmq.example;

/**
 * @author GaoH
 * @version 1.0.0
 * @since 2022/4/28 17:03
 */
public enum NameSrvEnum {

    DEV_SRV("132.126.3.240:9876");

    NameSrvEnum(String addr) {
        this.addr = addr;
    }

    /**
     * 名字服务器地址
     */
    String addr;

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }


    @Override
    public String toString() {
        return this.addr;
    }

    public static void main(String[] args) {
        System.out.println(NameSrvEnum.DEV_SRV);
    }

}
