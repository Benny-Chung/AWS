import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class Processor {

	public static void main(String[] args) throws InterruptedException {     
        AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                .withRegion("ap-northeast-1")
                .build();

        String bucketName = "images3358";         
        
        AmazonSQS sqs = AmazonSQSClientBuilder.standard()
                .withRegion("ap-northeast-1")
                .build();

        String inboxQueueUrl = "https://sqs.ap-northeast-1.amazonaws.com/713581367265/Inbox.fifo";
        String outboxQueueUrl = "https://sqs.ap-northeast-1.amazonaws.com/713581367265/Outbox.fifo";
        
        System.out.println("Polling messages from inboxQueue.\n");
        while (true) {
            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(inboxQueueUrl);
            List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
            for (Message message : messages) {
            	// Consume message
            	String key = message.getBody();
                System.out.println("Received " + key);
                String messageReceiptHandle = message.getReceiptHandle();
                sqs.deleteMessage(new DeleteMessageRequest(inboxQueueUrl, messageReceiptHandle));
                
                // Download image locally
                System.out.println("Downloading " + key);
                S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));
                System.out.println("Content-Type: "  + object.getObjectMetadata().getContentType());

                try {
                    S3Object o = s3.getObject(bucketName, key);
                    S3ObjectInputStream s3is = o.getObjectContent();
                    FileOutputStream fos = new FileOutputStream(new File(key));
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
                
                // Delete image in S3
                System.out.println("Deleting " + key);
                s3.deleteObject(bucketName, key);
                
                // Resize the image
                System.out.println("Resizing " + key);
				String Command = "convert -resize 50% " + key + " resize_" + key;
				try {
					Runtime rt = Runtime.getRuntime();
					Process p = rt.exec(Command);
					p.waitFor();
					p.destroy();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				// Put resized image to S3
	            s3.putObject(new PutObjectRequest(bucketName, "resize_"+key, new File("resize_"+key)));
	            
	            // Delete local images
                (new File(key)).delete();
                (new File("resize_"+key)).delete(); 
                
                // Send message to outboxQueue
                SendMessageRequest sendMessageRequest = new SendMessageRequest(outboxQueueUrl, "resize_"+key);
                sendMessageRequest.setMessageGroupId("resize_"+key);
                sqs.sendMessage(sendMessageRequest);
                System.out.println("Sending messages to outboxQueue.");
            }           
            Thread.sleep(5000);
        }

	}

}
