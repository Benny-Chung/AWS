
 

import java.io.IOException;
import java.io.InputStream;


import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
 
public class S3Util {
    private static final String BUCKET = "images3358";
    private static final String INQUEUEURL = "https://sqs.ap-northeast-1.amazonaws.com/713581367265/Inbox.fifo";
    private static final String OUTQUEUEURL = "https://sqs.ap-northeast-1.amazonaws.com/713581367265/Outbox.fifo";
    public static void uploadFile(String fileName, InputStream inputStream)
            throws S3Exception, AwsServiceException, SdkClientException, IOException {
         
        S3Client s3Client = S3Client.builder().region(Region.AP_NORTHEAST_1).build();
         
        PutObjectRequest s3Request = PutObjectRequest.builder()
                                .bucket(BUCKET)
                                .key(fileName)
                                .acl("public-read")
                                .build();
        s3Client.putObject(s3Request,
                RequestBody.fromInputStream(inputStream, inputStream.available()));
        
        SqsClient sqsClient = SqsClient.builder().region(Region.AP_NORTHEAST_1).build();
        
        SendMessageRequest sqsRequest = SendMessageRequest.builder()
        						.queueUrl(INQUEUEURL)
                                .messageGroupId(fileName)
                                .messageBody(fileName)
                                .build();
        sqsClient.sendMessage(sqsRequest);        
    }
    
}