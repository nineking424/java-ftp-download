package main;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;


public class FtpToTar {
	static int BUFFER_SIZE=1024*1024; // 1MB Buffer Size
	static String server = "192.168.0.14";
    static int port = 21;
    static String user = "***";
    static String pass = "***";
    static FTPClient client = new FTPClient();


	private static void loginFTP(FTPClient ftpClient) throws SocketException, IOException {
        ftpClient.connect(server, port);
        ftpClient.login(user, pass);
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
	}
	private static boolean retrieveFile(String sRemoteFilePath, String sLocalFilePath) throws IOException {
		
		boolean ret = false;
		
		FileOutputStream fos = new FileOutputStream(new File(sLocalFilePath));
		
		try(OutputStream os = new BufferedOutputStream(fos);
				InputStream is = client.retrieveFileStream(sRemoteFilePath)){
			
			if(is == null) {
				System.out.println("Remote File is not Exists : " + sRemoteFilePath);
				return ret;
			}
	        byte[] buffer = new byte[BUFFER_SIZE];
	        int bytesRead = -1;
	        while ((bytesRead = is.read(buffer)) != -1) {
	        	os.write(buffer, 0, bytesRead);
	        }
	        
	        ret = client.completePendingCommand();
		}
		return ret;
	}
	private static int retrieveFileAll(List<String> slRemoteFiles, String sOutDir) {

		int total = slRemoteFiles.size();
		int success = 0;
		String sRemoteFilePath, sRemoteFileName, sLocalFilePath;
		
		try {
			for(int i=0; i<total; i++) {
				sRemoteFilePath = slRemoteFiles.get(i);
				sRemoteFileName = Paths.get(sRemoteFilePath).getFileName().toString();
				sLocalFilePath = Paths.get(sOutDir, sRemoteFileName).toString();

		        System.out.println("Download... : " + sRemoteFilePath);
				if(retrieveFile(sRemoteFilePath, sLocalFilePath)) {
					success++;
				}
			}
		}catch(Exception ex) {
			ex.printStackTrace();
		}
        return success;
	}
    public static void main(String[] args) {
       	String sOutDir = "D:\\work\\ftp2tar\\output\\";
        try {
        	// 1) Login FTP
        	loginFTP(client);
            // 2) Make a list of remote files
        	List<String> slRemoteFiles = new ArrayList<String>();
        	for(int i=0; i < 1024; i++) {
        		slRemoteFiles.add("/nfs_svc/data/dummy-"+String.format("%04d", i+1)+".txt");
        	}
        	int ret = retrieveFileAll(slRemoteFiles, sOutDir);
        	
        	System.out.println(ret + " files are downloaded successfully.");
 
        } catch (IOException ex) {
            System.out.println("Error: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            try {
                if (client.isConnected()) {
                	client.logout();
                	client.disconnect();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
