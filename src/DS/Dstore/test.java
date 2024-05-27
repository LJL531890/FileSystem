package DS.Dstore;

//文件名 :fileStreamTest2.java
import java.io.*;
import java.util.Arrays;
import java.util.Scanner;

public class test {



        public static void main(String[] args) {
            Scanner scanner = new Scanner(System.in);
while(scanner.hasNextLine()) {
    System.out.printf("请输入你的名字: ");
    String name = scanner.nextLine();

    System.out.printf("请输入你的出生年份 : ");
    int age = scanner.nextInt();

    System.out.printf("请输入你喜欢的花 : ");
    String nan = scanner.nextLine();

    System.out.printf("你的名字是: " + name + "%n 你的出生年份是 :" + age + "%n 你喜欢的花是 :" + nan);
}
        }
    }

