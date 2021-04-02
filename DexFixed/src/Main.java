import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.Adler32;

public class Main {
    public static void main(String[] args) {
        if (args[0].equals("-h"))
        {
            System.out.println("把预加壳的APK追加到壳DEX文件中。");
            System.out.println("参数：[想要加壳的APK][壳APK的DEX文件]");
            return;
        }
        else if (args.length != 2)
        {
            for (int i = 0; i < args.length; ++i)
                System.out.println("args["+i+"]: " + args[i]);

            System.out.println("\r\n请输入正确的参数");
            System.out.println("参数：[想要加壳的APK][壳APK的DEX文件]");
            return;
        }

        System.out.println("读取APK文件...");
        try {
            File apkfile = new File(args[0]);
            System.out.println("APK文件的大小为：" + apkfile.length() + "字节");

            // 壳的dex文件存入到内存中
            File shellDexFile = new File(args[1]);
            System.out.println("读取DEX文件...");
            byte[] shellDexFileBuffer = readFileBytes(shellDexFile);
            System.out.println("DEX文件的大小为：" + shellDexFileBuffer.length + "字节");

            // 将原始的APK文件进行加密
            System.out.println("正在加密APK文件...");
            byte[] encryptedAPKFileBuffer = encrpt(readFileBytes(apkfile));
            System.out.println("APK文件加密完成");

            // 构造新的DEX
            System.out.println("开始构造新的DEX...");
            int lengthOfEncryptedAPK = encryptedAPKFileBuffer.length;
            int shellDexFileLength = shellDexFileBuffer.length;
            int newDexFileLength = lengthOfEncryptedAPK + shellDexFileLength + 4/*多出4字节是存放长度*/;
            System.out.println("新的DEX文件大小为：" + newDexFileLength + "字节");
            byte[] newDexFileBuffer = new byte[newDexFileLength];

            // 复制壳的DEX
            System.out.println("复制DEX文件...");
            System.arraycopy(shellDexFileBuffer, 0, newDexFileBuffer, 0, shellDexFileLength);
            // 复制加密后的APK
            System.arraycopy(encryptedAPKFileBuffer, 0, newDexFileBuffer, shellDexFileLength, lengthOfEncryptedAPK);//再在dex内容后面拷贝ap
            System.out.println("复制加密后的APK文件...");
            // 注明APK的长度
            System.arraycopy(intToByte(lengthOfEncryptedAPK), 0, newDexFileBuffer, newDexFileLength-4, 4);//最后4为长度

            // 校正新DEX文件的头信息
            System.out.println("正在校正新DEX文件...");
            fixFileSizeHeader(newDexFileBuffer);
            fixSHA1Header(newDexFileBuffer);
            fixCheckSumHeader(newDexFileBuffer);
            System.out.println("校正新DEX文件完成");

            // 将新DEX文件缓存保存文件
            System.out.println("保存文件...");
            String newDexFileName = args[1];
            File file = new File(newDexFileName);
            if (!file.exists()) {
                file.createNewFile();
            }

            // 输出文件
            FileOutputStream localFileOutputStream = new FileOutputStream(newDexFileName);
            localFileOutputStream.write(newDexFileBuffer);
            localFileOutputStream.flush();
            System.out.println("保存文件完成");
            // 释放资源
            localFileOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 自行选用加密方式
    private static byte[] encrpt(byte[] srcdata){
        for(int i = 0;i<srcdata.length;i++){
            srcdata[i] = (byte)(0xFF ^ srcdata[i]);
        }
        return srcdata;
    }

    // 修改dex头 file_size值
    private static void fixFileSizeHeader(byte[] dexBytes) {
        byte[] newFileBuffer = intToByte(dexBytes.length);
        byte[] refs = new byte[4];
        for (int i = 0; i < 4; i++) {
            refs[i] = newFileBuffer[newFileBuffer.length - 1 - i];
        }
        System.arraycopy(refs, 0, dexBytes, 32, 4);

        String hexstr = "";
        for (int i = 0; i < refs.length; i++) {
            hexstr += Integer.toString((refs[i] & 0xff) + 0x100, 16)
                    .substring(1);
        }
        System.out.println("FileSize：" + hexstr);
    }

    // 修改dex头 sha1值
    private static void fixSHA1Header(byte[] dexBytes) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
        messageDigest.update(dexBytes, 32, dexBytes.length - 32);
        byte[] newSha1Value = messageDigest.digest();
        System.arraycopy(newSha1Value, 0, dexBytes, 12, 20);//修改sha-1值（12-31）

        // 输出sha-1值
        String hexstr = "";
        for (int i = 0; i < newSha1Value.length; i++) {
            hexstr += Integer.toString((newSha1Value[i] & 0xff) + 0x100, 16)
                    .substring(1);
        }
        System.out.println("SHA1：" + hexstr);
    }

    // 修改dex头，CheckSum 校验码
    private static void fixCheckSumHeader(byte[] dexBytes) {
        Adler32 adler = new Adler32();

        // 从12到文件末尾计算校验码
        adler.update(dexBytes, 12, dexBytes.length - 12);
        long value = adler.getValue();
        int va = (int) value;
        byte[] newcs = intToByte(va);
        byte[] recs = new byte[4];
        for (int i = 0; i < 4; i++) {
            recs[i] = newcs[newcs.length - 1 - i];
        }
        System.arraycopy(recs, 0, dexBytes, 8, 4); //效验码赋值（8-11）

        System.out.println("CheckSum: " + Long.toHexString(value));
    }

    // int ==> byte[]
    public static byte[] intToByte(int number) {
        byte[] b = new byte[4];
        for (int i = 3; i >= 0; i--) {
            b[i] = (byte) (number % 256);
            number >>= 8;
        }
        return b;
    }

    // 以二进制读出文件内容
    private static byte[] readFileBytes(File file) throws IOException {
        byte[] arrayOfByte = new byte[1024];
        ByteArrayOutputStream localByteArrayOutputStream = new ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(file);
        while (true) {
            int i = fis.read(arrayOfByte);
            if (i != -1) {
                localByteArrayOutputStream.write(arrayOfByte, 0, i);
            } else {
                return localByteArrayOutputStream.toByteArray();
            }
        }
    }
}