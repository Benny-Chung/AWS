


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.io.*;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class Main {

    public static void main(String[] args) throws IOException {

        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider("default").getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (C:\\Users\\user\\.aws\\credentials), and is in valid format.",
                    e);
        }

        AmazonS3 s3 = AmazonS3ClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .withRegion("ap-northeast-1")
            .build();
        String bucketName = "images3358";
        String key = args[0];  
        
        AmazonSQS sqs = AmazonSQSClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion("ap-northeast-1")
                .build();
        String inboxQueueUrl = "https://sqs.ap-northeast-1.amazonaws.com/713581367265/Inbox.fifo";
        String outboxQueueUrl = "https://sqs.ap-northeast-1.amazonaws.com/713581367265/Outbox.fifo";
        
        try {
        	// Upload image to S3
            System.out.println("Uploading an image to S3.");
            s3.putObject(new PutObjectRequest(bucketName, key, new File(key)));
            
            // Send message to inboxQueue
            System.out.println("Sending a message to MyQueue.");
            SendMessageRequest sendMessageRequest = new SendMessageRequest(inboxQueueUrl, key);
            sendMessageRequest.setMessageGroupId(key);
            sqs.sendMessage(sendMessageRequest);
            
            System.out.println("Polling messages from outboxQueue.\n");
            while (true) {
                ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(outboxQueueUrl);
                List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
                for (Message message : messages) {

                	String keyNew = message.getBody();
                	// Consume message if receive "resize_"+key
                	if (keyNew.equals("resize_"+key)) {
                        System.out.println("Received resize_" + key);
                        String messageReceiptHandle = message.getReceiptHandle();
                        sqs.deleteMessage(new DeleteMessageRequest(outboxQueueUrl, messageReceiptHandle));
                        
                        // Download the resized image
                        System.out.println("Downloading resize_"+key);
                        try {
                            S3Object o = s3.getObject(new GetObjectRequest(bucketName, "resize_"+key));
                            S3ObjectInputStream s3is = o.getObjectContent();
                            FileOutputStream fos = new FileOutputStream(new File("resize_"+key));
                            byte[] read_buf = new byte[1024];
                            int read_len = 0;
                            while ((read_len = s3is.read(read_buf)) > 0) {
                                fos.write(read_buf, 0, read_len);
                            }
                            s3is.close();
                            fos.close();
                           
                        } catch (AmazonServiceException e) {
                            System.err.println(e.getErrorMessage());
                            System.exit(1);
                        } catch (FileNotFoundException e) {
                            System.err.println(e.getMessage());
                            System.exit(1);
                        } catch (IOException e) {
                            System.err.println(e.getMessage());
                            System.exit(1);
                        }
                        
                        // Delete the resized image
                        System.out.println("Deleting resize_" + key);
                        s3.deleteObject(bucketName, "resize_"+key);
                        System.exit(0);
                	}

                }		
            }


        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to Amazon S3, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with S3, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }


}

