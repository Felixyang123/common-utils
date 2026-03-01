package com.wly.samples.oss;

import com.alibaba.fastjson2.JSON;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;

import java.io.FileInputStream;
import java.net.URL;
import java.util.Date;

public class OssClient {

    public static void main(String[] args) throws Exception {
        // Endpoint以华东1（杭州）为例，其它Region请按实际情况填写。
        String endpoint = "https://oss.aliyuncs.com";
        // 从环境变量中获取访问凭证。运行本代码示例之前，请确保已设置环境变量OSS_ACCESS_KEY_ID和OSS_ACCESS_KEY_SECRET。
        // 填写Bucket名称，例如examplebucket。
        String bucketName = "examplebucket";
        // 填写Object完整路径，完整路径中不能包含Bucket名称，例如exampledir/exampleobject.txt。
        String objectName = "20260208/qrcode002.png";
        // 填写Bucket所在地域。以华东1（杭州）为例，Region填写为cn-hangzhou。
        String region = "cn-hangzhou";

        String path = "D:\\code\\common-utils\\spring-boot-samples\\src\\main\\resources\\statics\\qrcode-oss.png";
        // 创建凭证提供者
        DefaultCredentialProvider provider = new DefaultCredentialProvider("", "");

        // 创建OSSClient实例。
        // 当OSSClient实例不再使用时，调用shutdown方法以释放资源。
//        OSS ossClient = OSSClientBuilder.create()
//                .endpoint(endpoint)
//                .clientConfiguration(clientBuilderConfiguration)
//                .credentialsProvider(provider)
//                .build();

        OSS ossClient = new OSSClientBuilder().build(endpoint, provider);
        try {
            // 填写字符串。

            FileInputStream inputStream = new FileInputStream(path);
            URL url = new URL("");
            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, url.openStream());

            // 如果需要上传时设置存储类型和访问权限，请参考以下示例代码。
             ObjectMetadata metadata = new ObjectMetadata();
            // metadata.setHeader(OSSHeaders.OSS_STORAGE_CLASS, StorageClass.Standard.toString());
             metadata.setObjectAcl(CannedAccessControlList.PublicRead);
             putObjectRequest.setMetadata(metadata);

//            List<Bucket> buckets = ossClient.listBuckets();
//            System.out.println("成功连接到OSS服务，当前账号下的Bucket列表：");
//
//            if (buckets.isEmpty()) {
//                System.out.println("当前账号下暂无Bucket");
//            } else {
//                for (Bucket bucket : buckets) {
//                    System.out.println("- " + bucket.getName());
//                }
//            }

            PutObjectResult result = ossClient.putObject(putObjectRequest);
            System.out.println("result: "+ JSON.toJSONString(result));

            Date expiration = new Date(new Date().getTime() + 3600 * 1000L);
            URL presignedUrl = ossClient.generatePresignedUrl(bucketName, objectName, expiration);
            String file = presignedUrl.getFile();
            System.out.println(file);
            System.out.println(presignedUrl.getQuery());
            System.out.println(presignedUrl.getPath());


            // 下载Object到本地文件，并保存到指定的本地路径中。如果指定的本地文件存在会覆盖，不存在则新建。
            // 如果未指定本地路径，则下载后的文件默认保存到示例程序所属项目对应本地路径中。
//            ossClient.getObject(new GetObjectRequest(bucketName, objectName), new File(path));
//            System.out.println("oss resource url: " + result.getResponse().getUri());
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
}
