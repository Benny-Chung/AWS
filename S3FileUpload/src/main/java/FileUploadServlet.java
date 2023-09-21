

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;

 
@WebServlet("/upload")
@MultipartConfig(
        fileSizeThreshold = 1024*1024*2, // 2MB
        maxFileSize = 1024*1024*10, // 10MB
        maxRequestSize = 1024*1024*11   // 11MB
        )

public class FileUploadServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
    private static final String BUCKET = "images3358";
    private static final String OUTQUEUEURL = "https://sqs.ap-northeast-1.amazonaws.com/713581367265/Outbox.fifo";   
    public FileUploadServlet() {
        super();
    }

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String description = request.getParameter("description");
        System.out.println("Description: " + description);
         
        Part filePart = request.getPart("file");
         
        String fileName = getFileName(filePart);
         
        System.out.println("File name = " + fileName);
                   

        S3Util.uploadFile(fileName, filePart.getInputStream());
        
        SqsClient sqsClient = SqsClient.builder().region(Region.AP_NORTHEAST_1).build();
        
        ReceiveMessageRequest sqsRequest = ReceiveMessageRequest.builder()
        						.queueUrl(OUTQUEUEURL)
       						.build();
        
        fileName = "resize_" + fileName;
        outerLoop:
       	while (true) {
        	ReceiveMessageResponse res = sqsClient.receiveMessage(sqsRequest); 
        	if (res.hasMessages()) {
                List<Message> messages = res.messages();
            	for (Message m: messages) {
                	if (fileName.equals(m.body())) {
                        String messageReceiptHandle = m.receiptHandle();
                        DeleteMessageRequest delRequest = DeleteMessageRequest.builder()
        													.queueUrl(OUTQUEUEURL)
        													.receiptHandle(messageReceiptHandle)
        													.build();
                        sqsClient.deleteMessage(delRequest);
                        break outerLoop;
                	}
            	}
        	}
        	try {
        		Thread.sleep(5000);
        	} catch (Exception e) {};
        }
        
    	response.setContentType(filePart.getContentType());
        response.setHeader("Content-Disposition",
                           "attachment; filename=\""
                               + fileName + "\"");
                               
    	S3Client s3Client = S3Client.builder().region(Region.AP_NORTHEAST_1).build();
         
        GetObjectRequest s3GetRequest = GetObjectRequest.builder()
                                .bucket(BUCKET)
                                .key(fileName)
                                .build();
        ResponseInputStream<GetObjectResponse> s3objectResponse = s3Client.getObject(s3GetRequest);
        
        PrintWriter out = response.getWriter();
        int read_len = 0;
        while ((read_len = s3objectResponse.read()) != -1) {
            out.write(read_len);
        }
        s3objectResponse.close();
        out.close();
        
        DeleteObjectRequest s3DelRequest =  DeleteObjectRequest.builder()
										.bucket(BUCKET)
										.key(fileName)
										.build();
        s3Client.deleteObject(s3DelRequest);
    }
 
    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        int beginIndex = contentDisposition.indexOf("filename=") + 10;
        int endIndex = contentDisposition.length() - 1;
         
        return contentDisposition.substring(beginIndex, endIndex);
	}
    

}

