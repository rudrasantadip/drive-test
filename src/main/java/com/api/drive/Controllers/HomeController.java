package com.api.drive.Controllers;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.common.io.Files;

import jakarta.annotation.PostConstruct;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping
public class HomeController {

    private static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String USER_IDENTIFIER = "test";
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);

    private String callbackURI = "http://localhost:8080/oauth";
    Resource credentials = new FileSystemResource("tokens");
    Resource gdSecretkey = new ClassPathResource("keys/myjson.json");

    private GoogleAuthorizationCodeFlow flow;

    private void saveToken(String code) throws IOException {
        GoogleTokenResponse token_response = flow.newTokenRequest(code).setRedirectUri(callbackURI).execute();
        flow.createAndStoreCredential(token_response, USER_IDENTIFIER);
    }

    @PostConstruct
    public void init() throws Exception {
        GoogleClientSecrets secrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(gdSecretkey.getInputStream()));
        flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, secrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(credentials.getFile())).build();
    }

    @GetMapping("/data")
    public ResponseEntity<String> data() throws IOException {
        return ResponseEntity
                .ok(callbackURI + "<br>" + credentials.getURL().toString() + "<br>" + gdSecretkey.getURL().toString());
    }

    @GetMapping("/")
    public String showHome() throws IOException {
        Boolean isUserAuth = false;
        Credential credential = flow.loadCredential(USER_IDENTIFIER);
        if (credential != null) {
            boolean tokenValid = credential.refreshToken();
            if (tokenValid) {
                isUserAuth = true;
            }
        }
        return isUserAuth ? "dashbard.html" : "index.html";
    }

    @GetMapping(value = { "/googlesignin" })
    void googleSignin(HttpServletResponse response) throws IOException {
        GoogleAuthorizationCodeRequestUrl url = flow.newAuthorizationUrl();
        String redirectURL = url.setRedirectUri(callbackURI).setAccessType("offline").build();
        response.sendRedirect(redirectURL);
    }

    @GetMapping("/oauth")
    public String saveAuthorizationCode(HttpServletRequest request) throws IOException {
        String code = request.getParameter("code");
        if (code != null) {
            saveToken(code);
            return "dashboard.html";
        }
        return "index.html";
    }

    @GetMapping("/create")
    public void createFile(HttpServletResponse response) throws IOException {
        Credential cred = flow.loadCredential(USER_IDENTIFIER);
        Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred).setApplicationName("google-drive-test")
                .build();
        File file = new File();
        file.setName("Assissted_Signing_branch.jar");
        FileContent content = new FileContent("application/java-archive",
                new java.io.File("C:/Users/santa/OneDrive/Desktop/newFolder/Assissted_Signing_branch.jar"));
        File uploadedfile = drive.files().create(file, content).setFields("id").execute();
        String fileref = String.format("File Id: %s", uploadedfile.getId());
        response.getWriter().write(fileref);
    }

    @GetMapping("/filelist")
    @ResponseBody
    public String listFiles() throws IOException {
        Credential cred = flow.loadCredential(USER_IDENTIFIER);
        if (cred == null) {
            return "index.html";
        }
        Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred).setApplicationName("google-drive-test")
                .build();
        FileList list = drive.files().list()
                .setPageSize(10)
                .setFields("nextPageToken, files(id,name)")
                .execute();

        return list.getFiles().toString();
    }

    @ResponseBody
    @GetMapping("/file/{fileId}")
    public String getFile(@PathVariable String fileId) throws IOException {
        Credential cred = flow.loadCredential(USER_IDENTIFIER);
        if (cred == null) {
            return "index.html";
        }
        Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred).setApplicationName("google-drive-test")
                .build();
        File file = drive.files().get(fileId).execute();
        return file.toString();
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<String> download(@PathVariable String fileId) {
        try {
            Credential cred = flow.loadCredential(USER_IDENTIFIER);
            if (cred == null) {
                return ResponseEntity.ok("index.html");
            }
            Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred).setApplicationName("google-drive-test")
                    .build();
            File file = drive.files().get(fileId).execute();
            String targetDir = "C:\\Users\\santa\\OneDrive\\Desktop\\spring_boot";
            String filename = file.getName();
            String filepath = targetDir + filename;
            downloadFile(drive, fileId, filepath);
            return ResponseEntity.ok(String.format("File downloaded and saved at: %s", filepath));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    private void downloadFile(Drive drive, String fileId, String filePath) throws IOException {

    
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        drive.files().get(fileId).executeMediaAndDownloadTo(outputStream);
        byte [] filecontent = outputStream.toByteArray();
        System.out.println(filecontent);
        Files.write(filecontent, new java.io.File(filePath));
        outputStream.close();
    }

}
