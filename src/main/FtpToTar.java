package main;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;


public class FtpToTar {
	static int BUFFER_SIZE=1024*1024; // 1MB Buffer Size
	static String server = "192.168.0.14";
    static int port = 21;
    static String user = "nineking";
    static String pass = "***";
    static FTPClient client = new FTPClient();


	private static void loginFTP(FTPClient ftpClient) throws SocketException, IOException {
        ftpClient.connect(server, port);
        ftpClient.login(user, pass);
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
	}
	private static int getRemoteFileSize(String sRemoteFilePath) throws IOException {
		int size = 0;
		client.sendCommand("SIZE", sRemoteFilePath);
		String[] reply = client.getReplyString().split(" ");
		size = Integer.parseInt(reply[reply.length-1].trim());
		return size;
	}
	private static int retrieveFileAndTar(List<String> slRemoteFiles, String sOutDir, String sTarFilePath) {

		int total = slRemoteFiles.size();
		int success = 0;
		String sRemoteFilePath, sRemoteFileName, sLocalFilePath;

		try(
			// Make Tar file
            FileOutputStream tfos = new FileOutputStream(new File(sTarFilePath));
            BufferedOutputStream tbos = new BufferedOutputStream(tfos);
            TarArchiveOutputStream taos = new TarArchiveOutputStream(tbos)){
			
			for(int i=0; i<total; i++) {
				sRemoteFilePath = slRemoteFiles.get(i);
				sRemoteFileName = Paths.get(sRemoteFilePath).getFileName().toString();
				sLocalFilePath = Paths.get(sOutDir, sRemoteFileName).toString();

				// Retrieve File
		        File file = new File(sLocalFilePath);
				
				try(FileOutputStream fos = new FileOutputStream(file);
						OutputStream os = new BufferedOutputStream(fos);
						InputStream is = client.retrieveFileStream(sRemoteFilePath)){
					
					if(is == null) {
						System.out.println("Remote File is not Exists : " + sRemoteFilePath);
					}
			        byte[] buffer = new byte[BUFFER_SIZE];
			        int bytesRead = -1;
			        while ((bytesRead = is.read(buffer)) != -1) {
			        	os.write(buffer, 0, bytesRead);
			        }
			        
			        client.completePendingCommand();
				}
				// Archive Tar
				try (InputStream fis = new FileInputStream(file)){
	                TarArchiveEntry entry = new TarArchiveEntry(sRemoteFileName);
	                entry.setModTime(0);
	                entry.setSize(file.length());
	                taos.putArchiveEntry(entry);
			        byte[] buffer = new byte[BUFFER_SIZE];
			        int bytesRead = -1;
			        while ((bytesRead = fis.read(buffer)) != -1) {
			        	taos.write(buffer, 0, bytesRead);
			        }
	                taos.closeArchiveEntry();
				}
				file.delete();
			}
			// Make Tar
		}catch(Exception ex) {
			ex.printStackTrace();
		}
        return success;
	}

	private static int retrieveByteWithTar(List<String> slRemoteFiles, String sTarFilePath) {

		int total = slRemoteFiles.size();
		int success = 0;
		String sRemoteFilePath = "";
		String sRemoteFileName = "";
		
		try(
			// Make Tar file
            FileOutputStream tfos = new FileOutputStream(new File(sTarFilePath));
            BufferedOutputStream tbos = new BufferedOutputStream(tfos);
            TarArchiveOutputStream taos = new TarArchiveOutputStream(tbos)){
            
			for(int i=0; i<total; i++) {
				sRemoteFilePath = slRemoteFiles.get(i);
				sRemoteFileName = Paths.get(sRemoteFilePath).getFileName().toString();

				try(ByteArrayOutputStream baos = new ByteArrayOutputStream();
						OutputStream os = new BufferedOutputStream(baos);
						InputStream is = client.retrieveFileStream(sRemoteFilePath)){

					// Start : Download
					if(is == null) {
						System.out.println("Remote File is not Exists : " + sRemoteFilePath);
						break;
					}
			        byte[] buffer = new byte[BUFFER_SIZE];
			        int bytesRead = -1;
			        while ((bytesRead = is.read(buffer)) != -1) {
			        	os.write(buffer, 0, bytesRead);
			        }
			        client.completePendingCommand();
			        // End : Download

			        // Start : Tar Archive
	                TarArchiveEntry entry = new TarArchiveEntry(sRemoteFileName);
	                entry.setModTime(0);
	                entry.setSize(baos.size());
	                taos.putArchiveEntry(entry);
	                taos.write(baos.toByteArray());
	                taos.closeArchiveEntry();
	                baos.close();
			        // End : Tar Archive
				}
			}
			
		}catch(Exception ex) {
			ex.printStackTrace();
		}
        return success;
	}
	private static int retrieveByteWithTarDirect(List<String> slRemoteFiles, String sTarFilePath) {

		int total = slRemoteFiles.size();
		int success = 0;
		String sRemoteFilePath = "";
		String sRemoteFileName = "";
		
		try(
			// Make Tar file
            FileOutputStream tfos = new FileOutputStream(new File(sTarFilePath));
            BufferedOutputStream tbos = new BufferedOutputStream(tfos);
            TarArchiveOutputStream taos = new TarArchiveOutputStream(tbos)){
            
			for(int i=0; i<total; i++) {
				sRemoteFilePath = slRemoteFiles.get(i);
				sRemoteFileName = Paths.get(sRemoteFilePath).getFileName().toString();
				
				int size = getRemoteFileSize(sRemoteFilePath);

				try(InputStream is = client.retrieveFileStream(sRemoteFilePath)){
					if(is == null) {
						System.out.println("Remote File is not Exists : " + sRemoteFilePath);
						break;
					}
	                TarArchiveEntry entry = new TarArchiveEntry(sRemoteFileName);
	                entry.setModTime(0);
	                entry.setSize(size);
	                taos.putArchiveEntry(entry);

			        byte[] buffer = new byte[BUFFER_SIZE];
			        int bytesRead = -1;
			        while ((bytesRead = is.read(buffer)) != -1) {
			        	taos.write(buffer, 0, bytesRead);
			        }
			        client.completePendingCommand();
	                taos.closeArchiveEntry();
				}
			}
			
		}catch(Exception ex) {
			ex.printStackTrace();
		}
        return success;
	}
    public static void main(String[] args) {
       	String sOutDir = "D:\\work\\ftp2tar\\output\\";
       	String sTarFilePath = "D:\\work\\ftp2tar\\output\\output.tar";
        try {
        	// 1) Login FTP
        	loginFTP(client);
            // 2) Make a list of remote files
        	List<String> slRemoteFiles = new ArrayList<String>();
        	for(int i=0; i < 1024; i++) {
        		slRemoteFiles.add("/nfs_svc/data/dummy-"+String.format("%04d", i+1)+".txt");
        	}
        	int ret;
        	
        	long stime = System.currentTimeMillis();
        	long etime = System.currentTimeMillis();
        	
        	long s1, s2, s3, s4;
        	long e1, e2, e3;
        	
        	s1 = System.currentTimeMillis();

	        System.out.println("retrieveFileAndTar...");
        	ret = retrieveFileAndTar(slRemoteFiles, sOutDir, sTarFilePath);
        	s2 = System.currentTimeMillis();

	        System.out.println("retrieveByteWithTar...");
        	ret = retrieveByteWithTar(slRemoteFiles, sTarFilePath);
        	s3 = System.currentTimeMillis();

	        System.out.println("retrieveByteWithTarDirect...");
        	ret = retrieveByteWithTarDirect(slRemoteFiles, sTarFilePath);
        	s4 = System.currentTimeMillis();
        	
        	e1 = s2 - s1;
        	e2 = s3 - s2;
        	e3 = s4 - s3;

        	System.out.println("ELAPSED 1 : " + e1 + " ms");
        	System.out.println("ELAPSED 2 : " + e2 + " ms");
        	System.out.println("ELAPSED 3 : " + e3 + " ms");
 
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
