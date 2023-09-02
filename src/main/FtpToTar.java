package main;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;


public class FtpToTar {
	static String server = "192.168.0.14";
    static int port = 21;
    static String user = "nineking";
    static String pass = "****";
    static FTPClient client = new FTPClient();


	private static boolean loginFTP(FTPClient ftpClient)  {
		boolean success = false;
		
		try {
			ftpClient.connect(server, port);
	        if(ftpClient.login(user, pass)) {
		        ftpClient.enterLocalPassiveMode();
		        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
		        success = true;
	        }
		}catch(Exception ex) {
			// do nothing
		}
        return success;
	}
	private static int getRemoteFileSize(String sRemoteFilePath) throws IOException {
		int size = -1;
		String reply="";
		try {
			client.sendCommand("SIZE", sRemoteFilePath);
			reply = client.getReplyString();
			String[] token = reply.split(" ");
			size = Integer.parseInt(token[token.length-1].trim());
		}catch(Exception ex){
			// do nothing
			ex.printStackTrace();
			System.out.println("reply : " + reply);
		}		
		
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
						continue;
					}
			        IOUtils.copy(is, os);
			        client.completePendingCommand();
				}
				// Archive Tar
				try (InputStream fis = new FileInputStream(file)){
	                TarArchiveEntry entry = new TarArchiveEntry(sRemoteFileName);
	                entry.setModTime(0);
	                entry.setSize(file.length());
	                taos.putArchiveEntry(entry);
	                IOUtils.copy(fis, taos);
	                taos.closeArchiveEntry();
				}
				file.delete();
				success++;
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
						BufferedOutputStream bos = new BufferedOutputStream(baos);
						InputStream is = client.retrieveFileStream(sRemoteFilePath)){
					//retrieve
					if(is == null) {
						System.out.println("Remote File is not Exists : " + sRemoteFilePath);
						continue;
					}
			        IOUtils.copy(is, bos);
			        client.completePendingCommand();
			        // tar
	                TarArchiveEntry entry = new TarArchiveEntry(sRemoteFileName);
	                entry.setModTime(0);
	                entry.setSize(baos.size());
	                taos.putArchiveEntry(entry);
	                baos.writeTo(taos);
	                taos.closeArchiveEntry();
	                baos.close();
				}
				success++;
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
				if(size <= 0) {
					System.out.println("Fail to get remote file info: " + sRemoteFilePath);
					continue;
				}
				try(InputStream is = client.retrieveFileStream(sRemoteFilePath)){
					if(is == null || size < 0) {
						System.out.println("Remote File is not Exists : " + sRemoteFilePath);
						continue;
					}
	                TarArchiveEntry entry = new TarArchiveEntry(sRemoteFileName);
	                entry.setModTime(0);
	                entry.setSize(size);
	                taos.putArchiveEntry(entry);
			        IOUtils.copy(is, taos);
			        client.completePendingCommand();
	                taos.closeArchiveEntry();
				}
				success++;
			}
			
		}catch(Exception ex) {
			ex.printStackTrace();
		}
        return success;
	}
    public static void main(String[] args) {
       	String sOutDir = "D:\\work\\ftp2tar\\output\\";
       	String sTarFilePath = "D:\\work\\ftp2tar\\output\\output.tar";

        // Make a list of remote files
    	List<String> slRemoteFiles = new ArrayList<String>();
    	for(int i=0; i < 1024; i++) {
    		slRemoteFiles.add("/nfs_svc/data/dummy-"+String.format("%04d", i+1)+".txt");
    	}
    	
        try {
           	long s1, s2, s3, s4;
        	long e1, e2, e3;

        	// 1) Login FTP
        	if(!loginFTP(client)) {
        		System.out.println("Login fail...");
        		return;
        	}
        	
        	s1 = System.currentTimeMillis();

	        System.out.println("retrieveFileAndTar...");
        	retrieveFileAndTar(slRemoteFiles, sOutDir, sTarFilePath);
        	s2 = System.currentTimeMillis();

	        System.out.println("retrieveByteWithTar...");
        	retrieveByteWithTar(slRemoteFiles, sTarFilePath);
        	s3 = System.currentTimeMillis();

	        System.out.println("retrieveByteWithTarDirect...");
        	retrieveByteWithTarDirect(slRemoteFiles, sTarFilePath);
        	s4 = System.currentTimeMillis();
        	
        	e1 = s2 - s1;
        	e2 = s3 - s2;
        	e3 = s4 - s3;

        	System.out.println("ELAPSED 1 : " + e1 + " ms");
        	System.out.println("ELAPSED 2 : " + e2 + " ms");
        	System.out.println("ELAPSED 3 : " + e3 + " ms");
 
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
