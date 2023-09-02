# java-ftp-download
# Performance test : FTP Download and Tar Archiving

[https://www.codejava.net/java-se/ftp/java-ftp-file-download-tutorial-and-example](https://www.codejava.net/java-se/ftp/java-ftp-file-download-tutorial-and-example)

# FTP 다운로드 및 TAR 묶음 작업에 대하여 방식에 따른 성능 차이 비교

- 본 테스트에서 가정한 작업은 FTP를 사용해 다운받은 파일을 tar묶어 최종 결과물로 제공하는 것이다. 원격지에 있는 파일은 FTP로 다운받은 후 별 다른 용처 없이 곧바로 tar로 묶음 처리된다. 이처럼, 불필요한 원본파일의 생성으로 인한 overhead를 확인하고 이를 개선하기 위한 방법을 비교/분석하는것이 본 테스트의 주 목적이다.
- 본 project에서 시도한 방법은 아래와 같다.
    1. FTP로 각 파일을 다운받아 디스크에 저장, tar에 이를 기록한다.
    2. FTP로 각 파일을 다운받아 메모리에 저장, tar에 이를 기록한다.
    3. FTP로 각 파일을 읽음과 동시에 tar에 이를 기록한다.
- 테스트 결과, 방법 2가 방법1 대비 약 2배 가량 높은 성능을 보여주었으며, 방법3의 경우는 방법2 대비 10%가량 높은 성능을 보여주었다.
- 본 테스트에서 사용한 코드는 불필요한 부분을 최대한 제거하였다. 실제 운영 코드는 이보다 더 복잡하고 많은 작업을 수행하기 때문에, 방식에 따른 성능 차이는 더 크게 나타날 것으로 예상되며, 방식1보단 무조건 2를 추천한다.
- 방법3의 경우, 각각의 I/O Stream을 직접 연결하기 때문에 TarArchiveEntry를 만들기 전에 각 파일의 사이즈를 알 수 있는 방법이 없다. 따라서 이를 위해 FTP 커맨드(=SIZE)를 추가로 요청하게 되는데, 이로 인해 하나의 파일 당 두 번의 FTP 요청을 보내게 된다. 이는 곧, 원래도 불안정한 FTP의 안정성을 두 배나 저해하는 결과를 불러오게 되며 성능 증가폭(10%) 대비 에러 발생률 증가(2배)를 감안 하였을때, 방식 3보다는 방식2를 추천한다.
- 

# 방법 1

FTP로 파일을 모두 다운로드 받은 다음 TAR를 생성한다.

```java
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
				success++;
			}
			// Make Tar
		}catch(Exception ex) {
			ex.printStackTrace();
		}
        return success;
	}
```

# 방법2

FTP 파일을 바이트로 메모리에 읽어들인 후 TAR로 묶는다.

```java
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
						continue;
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
				success++;
			}
			
		}catch(Exception ex) {
			ex.printStackTrace();
		}
        return success;
	}
```

# 방법3

FTP와 TAR IO스트림을 직접 연결하여 곧바로 TAR에 기록한다.

```java
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

			        byte[] buffer = new byte[BUFFER_SIZE];
			        int bytesRead = -1;
			        while ((bytesRead = is.read(buffer)) != -1) {
			        	taos.write(buffer, 0, bytesRead);
			        }
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
```
