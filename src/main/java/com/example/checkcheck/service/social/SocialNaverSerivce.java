package com.example.checkcheck.service.social;

import com.example.checkcheck.dto.responseDto.SocialResponseDto;
import com.example.checkcheck.dto.responseDto.TokenFactory;
import com.example.checkcheck.dto.userinfo.NaverUserInfoDto;
import com.example.checkcheck.model.Member;
import com.example.checkcheck.model.RefreshToken;
import com.example.checkcheck.repository.RefreshTokenRepository;
import com.example.checkcheck.repository.MemberRepository;
import com.example.checkcheck.security.UserDetailsImpl;
import com.example.checkcheck.service.MemberService;
import com.example.checkcheck.util.ComfortUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
@RequiredArgsConstructor
@Service
public class SocialNaverSerivce {


    @Value("${cloud.security.oauth2.client.registration.naver.client-id}")
    private String client_id;
    @Value("${cloud.security.oauth2.client.registration.naver.client-secret}")
    private String clientSecret;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberService memberService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ComfortUtils comfortUtils;

    @Transactional
    public SocialResponseDto naverLogin(String code, String state, HttpServletResponse response) {

        try {
            // ??????????????? ????????? ???????????? + ?????? ???????????? ??????
            NaverUserInfoDto naverUser = getNaverUserInfo(code, state);
            String password = UUID.randomUUID().toString();
            String encodedPassword = passwordEncoder.encode(password);
            String provider = "naver";
            String naverUserEmail = naverUser.getUserEmail().substring(1, naverUser.getUserEmail().length() - 1);
            String realEmail = "n_" + naverUserEmail;



            // ????????? ??????
            // ????????? ID??? ?????? ?????? DB ?????? ??????
            Member member = memberRepository.findByUserEmail(realEmail).orElse(null);


            // ????????? ????????????
            if (member == null) {
                member = Member.builder()
                        .nickName(naverUser.getNickname().substring(1, naverUser.getNickname().length() - 1))
                        .password(encodedPassword)
                        .userEmail("n_"+naverUserEmail)
                        .userRealEmail(naverUser.getUserEmail().substring(1, naverUser.getUserEmail().length() - 1))
//                        .socialId(Double.valueOf(naverUser.getNaverId().substring(1, naverUser.getNaverId().length() - 1)))
                        .createdAt(LocalDateTime.now())
                        .provider(provider)
                        .build();
                memberRepository.save(member);

            } else {
                // ?????? ?????????
                UserDetailsImpl userDetails = new UserDetailsImpl(member);
                Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }


            // User ?????? ??????


//            ?????? ??????
            TokenFactory tokenFactory = memberService.accessAndRefreshTokenProcess(member.getUserEmail(), response);

            String refreshToken =  tokenFactory.getRefreshToken();

            String accessToken = naverUser.getAccessToken();


//        ???????????????????????? & ???????????? ?????????
            Optional<RefreshToken> existToken = refreshTokenRepository.findByTokenKey(member.getUserEmail());
            RefreshToken token = new RefreshToken(member.getUserEmail(), refreshToken);
            if (existToken.isEmpty()) {
                refreshTokenRepository.save(token);
            } else {
                existToken.get().setTokenKey(token.getTokenKey());
                existToken.get().setTokenValue(token.getTokenValue());
            }


            SocialResponseDto socialResponseDto = SocialResponseDto.builder()
                    .nickName(member.getNickName()) // 1
                    .userEmail(member.getUserEmail())
                    .accessToken(tokenFactory.getAccessToken())
                    .refreshToken(tokenFactory.getRefreshToken())
                    .userRank(comfortUtils.getUserRank(member.getPoint()))
                    .build();
            return socialResponseDto;

        } catch (IOException e) {
            return null;
        }
    }


    // ???????????? ???????????? ????????? ?????? ?????? ?????????
    public JsonElement jsonElement(String reqURL, String token, String code, String state) throws IOException {

        // ???????????? URL ??????
        URL url = new URL(reqURL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // POST ????????? ?????? ???????????? false??? setDoOutput??? true???
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        // POST ????????? ????????? ????????? ?????? ??? ??????
        if (token == null) {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream()));
            String sb = "grant_type=authorization_code" +
                    "&client_id="+client_id +
                    "&client_secret="+clientSecret +
                    "&redirect_uri=http://localhost:8080/user/signin/naver" +
                    "&code=" + code +
                    "&state=" + state;
            bw.write(sb);
            bw.flush();
            bw.close();
        } else {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }

        // ????????? ?????? ?????? JSON????????? Response ????????? ????????????
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        StringBuilder result = new StringBuilder();

        while ((line = br.readLine()) != null) {
            result.append(line);
        }
        br.close();

        // Gson ?????????????????? ????????? ???????????? JSON ??????
        return JsonParser.parseString(result.toString());
    }


    // ???????????? ???????????? ???????????? ?????? ?????????
    public NaverUserInfoDto getNaverUserInfo(String code, String state) throws IOException {

        String codeReqURL = "https://nid.naver.com/oauth2.0/token";
        String tokenReqURL = "https://openapi.naver.com/v1/nid/me";


        // ????????? ???????????? ???????????? ????????? ?????? ?????????
        JsonElement tokenElement = jsonElement(codeReqURL, null, code, state);
        String access_Token = tokenElement.getAsJsonObject().get("access_token").getAsString();
        String refresh_token = tokenElement.getAsJsonObject().get("refresh_token").getAsString();

        // ????????? ????????? ???????????? ???????????? ???????????? ?????????
        JsonElement userInfoElement = jsonElement(tokenReqURL, access_Token, null, null);
        String naverId = String.valueOf(userInfoElement.getAsJsonObject().get("response")
                .getAsJsonObject().get("id"));
        String userEmail = String.valueOf(userInfoElement.getAsJsonObject().get("response")
                .getAsJsonObject().get("email"));
        String nickName = String.valueOf(userInfoElement.getAsJsonObject().get("response")
                .getAsJsonObject().get("name"));

//        String email = "n_" + userEmail;
        return new NaverUserInfoDto(naverId, nickName, userEmail, access_Token, refresh_token);
    }

}
