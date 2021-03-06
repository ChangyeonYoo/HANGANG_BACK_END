package in.hangang.serviceImpl;

import in.hangang.domain.AuthNumber;
import in.hangang.domain.User;
import in.hangang.enums.ErrorMessage;
import in.hangang.enums.Major;
import in.hangang.exception.AccessTokenInvalidException;
import in.hangang.exception.RefreshTokenExpireException;
import in.hangang.exception.RefreshTokenInvalidException;
import in.hangang.exception.RequestInputException;
import in.hangang.mapper.UserMapper;
import in.hangang.service.UserService;
import in.hangang.util.Jwt;
import in.hangang.util.S3Util;
import in.hangang.util.SesSender;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.util.*;


@Transactional
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private Jwt jwt;
    @Value("${refresh.user.name}")
    private String refreshUserName;
    @Value("${token.user.name}")
    private String accessTokenName;
    @Resource
    private SesSender sesSender;
    @Resource
    private SpringTemplateEngine springTemplateEngine;
    @Resource
    private S3Util s3Util;

    @Value("${token.access}")
    private String access_token;

    @Value("${token.refresh}")
    private String refresh_token;


    public Map<String,String> login(User user) throws Exception{

        User dbUser = userMapper.getPasswordFromPortal(user.getPortal_account());

        // 아이디 중복 검사
        if ( dbUser == null){
            throw new RequestInputException(ErrorMessage.REQUEST_INVALID_EXCEPTION);
        }
        // 비밀번호가 틀린 경우
        if ( !BCrypt.checkpw( user.getPassword(), dbUser.getPassword() )){
            throw new RequestInputException(ErrorMessage.REQUEST_INVALID_EXCEPTION);
        }
        // 로그인이 성공한 경우 , access token, refresh token 반환
        else{
            Map<String, String> token = new HashMap<>();
            token.put(access_token, jwt.generateToken(dbUser.getId(), dbUser.getNickname(), access_token) );
            token.put(refresh_token, jwt.generateToken(dbUser.getId(),dbUser.getNickname(),refresh_token));
            return token;
        }
    }

    private void setMajor(String major, Long user_id) throws Exception{

        if (user_id == null){
            throw new RequestInputException(ErrorMessage.REQUEST_INVALID_EXCEPTION);
        }

        boolean check = false;
        for( Major majors : Major.values()){
            if ( major.equals(( String.valueOf(majors)) )){
                check = true;
            }
        }

        if (check)
            userMapper.setMajor(major,user_id);
        else
            throw new RequestInputException(ErrorMessage.MAJOR_INVALID_EXCEPTION);
    }

    public void signUp(User user) throws Exception {

        //이메일 인증 여부 체크
        ArrayList<AuthNumber> list = userMapper.getAuthTrue(user.getPortal_account(),0);
        if ( list.size() == 0){
            throw new RequestInputException(ErrorMessage.EMAIL_NONE_AUTH_EXCEPTION);
        }

        //전공 null 체크 validation에서 major = [] 로만 보내면 null이 잡히지않는 에러발견
        // major = ["" ] 은 잡힘
        if  (user.getMajor().size() == 0 ){
            throw new RequestInputException(ErrorMessage.MAJOR_INVALID_EXCEPTION);
        }

        // portal account 를 통한 중복가입 여부 확인
        if ( user == null || user.getPortal_account() == null || userMapper.getUserIdFromPortal(user.getPortal_account() ) != null ) {
            throw new RequestInputException(ErrorMessage.REQUEST_INVALID_EXCEPTION);
        }

        // 닉네임 null,중복 체크
        if (user.getNickname() != null) {
            if (userMapper.getUserByNickName(user.getNickname() ) != null ) {
                throw new RequestInputException(ErrorMessage.REQUEST_INVALID_EXCEPTION);
            }
        }

        // 암호화
        user.setPassword( BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()) );
        userMapper.signUp(user); // 회원가입

        // 회원가입 완료 시  phoneNumber, flag, ip가 같은 이전 이력은 모두 만료 + soft delete 시킴
        AuthNumber authNumber = new AuthNumber();
        authNumber.setIp(this.getClientIp());
        authNumber.setPortal_account(user.getPortal_account());
        authNumber.setFlag(0); // 회원가입 인증
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_YEAR, -1); // 현재시간 빼기 하루
        authNumber.setExpired_at(new Timestamp(calendar.getTimeInMillis()));
        userMapper.expirePastAuthNumber(authNumber);

        //회원가입후 user의 가입된 id를 구함
        Long user_id = userMapper.getUserIdFromPortal(user.getPortal_account());

         // n개의 전공을 삽입
        for ( int i=0; i< user.getMajor().size(); i++){
            setMajor(user.getMajor().get(i), user_id);
        }

        // user salt = timestamp + user_id + BCrypt
        // salt 삽입
        calendar.setTime(new Date());
        String salt = user_id.toString() + calendar.getTime();
        salt = (BCrypt.hashpw(salt , BCrypt.gensalt()));
        userMapper.setSalt(salt,user_id);

    }

    public Map<String,Object> refresh()throws Exception{
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String refreshToken = request.getHeader(refreshUserName);
        int result = jwt.isValid(refreshToken,1);
        if (result == 1){ // valid하다면
            Map<String,Object> payloads = jwt.validateFormat(refreshToken,1);
            Long id = Long.valueOf(String.valueOf( payloads.get("id")));
            String nickname = String.valueOf( payloads.get("nickname"));
            Map<String,Object> token = new HashMap<>();
            token.put(access_token, jwt.generateToken(id, nickname, access_token) );
            token.put(refresh_token, jwt.generateToken(id,nickname,refresh_token));
            return token;
        }
        else if (  result == 0 ){
            throw new RefreshTokenInvalidException(ErrorMessage.REFRESH_FORBIDDEN_AUTH_INVALID_EXCEPTION); // REFRESH 토근에 ACCESS 토근이 들어온 경우
        }
        else{
            throw new RefreshTokenInvalidException(ErrorMessage.UNDEFINED_EXCEPTION); // pass도 expire도 invalid도 아닌경우 발생
        }
    }

    public String sendEmail(AuthNumber authNumber) throws Exception {

        //회원가입 요청이라면 , 가입한 아이디이면 throw
        if (authNumber.getFlag() == 0) {

            Long id = userMapper.getUserIdFromPortal(authNumber.getPortal_account());
            if (id != null) {
                throw new RequestInputException(ErrorMessage.EMAIL_ALREADY_AUTHED);
            }
        }
        //비밀번호 찾기 요청이라면, 가입하지 않은 아이디면 throw
        if (authNumber.getFlag() == 1) {

            Long id = userMapper.getUserIdFromPortal(authNumber.getPortal_account());
            if (id == null) {
                throw new RequestInputException(ErrorMessage.NO_USER_EXCEPTION);
            }
        }

        // 1일 5회 요청제한을 넘겼는지
        String ip = this.getClientIp();
        Calendar calendar = Calendar.getInstance(); // 싱글톤 객체라긔
        calendar.setTime(new Date());
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        calendar.set(year,month,day,0,0 ,0); // 해당 날짜의 00시 00분 00초
        Timestamp start =  new Timestamp(calendar.getTimeInMillis());
        calendar.set(year,month,day,23,59 ,59); // 해당 날짜의 23시 59분 59초
        Timestamp end =  new Timestamp(calendar.getTimeInMillis());
        Integer count = userMapper.authNumberAllSoftDeleteAfterUse(authNumber.getPortal_account(),ip,start, end);
        System.out.println(count);
        if ( count >= 5 ){
            throw new RequestInputException(ErrorMessage.EMAIL_COUNT_EXCEED_EXCEPTION); // 요청한 날의 요청횟수가 5번을 초과한경우
        }

        // 재전송의 경우 phoneNumber, flag, ip가 같은 이전 이력은 모두 만료 + soft delete 시킴
        authNumber.setIp(ip);
        calendar.setTime(new Date()); // 다시 현재시간
        calendar.add(Calendar.DAY_OF_YEAR, -1); // 현재시간 빼기 하루
        authNumber.setExpired_at(new Timestamp(calendar.getTimeInMillis()));
        userMapper.expirePastAuthNumber(authNumber);



        //get random string for secret String
        Random rnd = new Random();
        String secret = "";
        for( int i=0; i<6; i++){
            //secret += String.valueOf((char) ((int) (rnd.nextInt(26)) + 97)); // 6글자의 random string
            secret  += rnd.nextInt(10);// 글자의 random numbers
        }
        Context context = new Context();
        context.setVariable("secret",secret );


        //set auth_number to database
        authNumber.setSecret(secret);

        calendar.setTime(new Date());
        calendar.add(Calendar.MINUTE, 10); // 만료기한 10분
        authNumber.setExpired_at(new Timestamp( (calendar.getTime()).getTime()));
        authNumber.setIp(this.getClientIp());

        userMapper.setAuthNumber(authNumber);


        // send mail to portal_account email
        String body = null;
        if ( authNumber.getFlag() == 0) {
            body = springTemplateEngine.process("signUpEmail", context);
            sesSender.sendMail("no-reply@bcsdlab.com", authNumber.getPortal_account(), "한강 서비스 회원가입 인증", body);
        }
        else if ( authNumber.getFlag() == 1) {
            body = springTemplateEngine.process("findPassword", context);
            sesSender.sendMail("no-reply@bcsdlab.com", authNumber.getPortal_account(), "한강서비스 비밀번호 재발급 인증", body);
        }

        return "Email을 발송했습니다.";
    }
   public boolean configEmail(AuthNumber authNumber) throws Exception{


       //회원가입 요청이라면 , 가입한 아이디이면 throw
       if (authNumber.getFlag() == 0) {

           Long id = userMapper.getUserIdFromPortal(authNumber.getPortal_account());
           if (id != null) {
               throw new RequestInputException(ErrorMessage.EMAIL_ALREADY_AUTHED);
           }
       }
       //비밀번호 찾기 요청이라면, 가입하지 않은 아이디면 throw
       if (authNumber.getFlag() == 1) {

           Long id = userMapper.getUserIdFromPortal(authNumber.getPortal_account());
           if (id == null) {
               throw new RequestInputException(ErrorMessage.NO_USER_EXCEPTION);
           }
       }
        //portal account, flag, is_deleted = 0 값으로 select 해옴
        ArrayList<AuthNumber> list = userMapper.getSecret(authNumber);

        //메일로 보낸적 없다면 email인증을 신청하라고 알림
        if (list.size() == 0){
            throw new RequestInputException(ErrorMessage.EMAIL_NONE_AUTH_EXCEPTION);
        }

        //request secret값이 일치하는지? request client의 ip값이 일치하는지?
        AuthNumber dbAuthNumber = null;
        String ip = this.getClientIp();
        for(int i=0;i<list.size();i++){
            if ( authNumber.getSecret().equals(list.get(i).getSecret()) && ip.equals(list.get(i).getIp())){
                dbAuthNumber = list.get(i);
                break;
            }
        }

        // ip가 다른경우도 따로 처리해야함, 현재는 공통으로 invalid 처리
        // portal으로 가져왔으나 secret 값이 다르다면 인증번호를 확인하라는 알림
        if ( dbAuthNumber == null){
            throw new RequestInputException(ErrorMessage.EMAIL_SECRET_INVALID_EXCEPTION);
        }

        //만료시간보다 현재시간이 크다면 만료되었다고 알림
        Timestamp exp = dbAuthNumber.getExpired_at();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        System.out.println(exp );
        System.out.println(now );
        if( exp.getTime() < now.getTime()) {
            userMapper.authNumberSoftDelete(dbAuthNumber.getId()); // 만료시 soft delete
            throw new RequestInputException(ErrorMessage.EMAIL_EXPIRED_AUTH_EXCEPTION);
        }
        // 만료되지않았고 / secret 같고 // portal 같고 / ip 같다면 ==> is_authed = 1
        if ( dbAuthNumber.getSecret().equals(authNumber.getSecret())){
            userMapper.setIs_authed(true,dbAuthNumber.getId(), dbAuthNumber.getFlag());
            return true;
        }
        else{
            return false;
        }
    }

    private String getClientIp(){
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String ip =request.getHeader("X-Forwarded-For");
        if (ip == null) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null) {
            ip = request.getHeader("WL-Proxy-Client-IP"); // 웹로직
        }
        if (ip == null) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
    public boolean checkNickname(String nickname) {
        if (userMapper.getUserByNickName(nickname) == null)
            return true;
        else
            return false;
    }

    public void findPassword(User user) throws  Exception{
        Long id = userMapper.getUserIdFromPortal(user.getPortal_account());
        // 없는 아이디는 아닌지?
        if (id == null) {
            throw new RequestInputException(ErrorMessage.NO_USER_EXCEPTION);
        }

        //이메일 인증 여부 체크
        ArrayList<AuthNumber> list = userMapper.getAuthTrue(user.getPortal_account(),1);
        if ( list.size() == 0){
            throw new RequestInputException(ErrorMessage.EMAIL_NONE_AUTH_EXCEPTION);
        }
        // 비밀번호 암호화
        user.setPassword( BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()) );

        // 비밀번호 찾기 완료 시  phoneNumber, flag, ip가 같은 이전 이력은 모두 만료 + soft delete 시킴
        AuthNumber authNumber = new AuthNumber();
        authNumber.setIp(this.getClientIp());
        authNumber.setPortal_account(user.getPortal_account());
        authNumber.setFlag(1); // 비밀번호 찾기 인증
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_YEAR, -1); // 현재시간 빼기 하루
        authNumber.setExpired_at(new Timestamp(calendar.getTimeInMillis()));
        userMapper.expirePastAuthNumber(authNumber);

        userMapper.findPassword(user);
    }

    // token의 id를 가져와 User를 반환하는 Method
    public User getLoginUser() throws Exception{
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String token = request.getHeader(accessTokenName);
        if ( token == null){
            return null;
        }
        else {
            // user id로 User를 select 하는것은 자유롭게 해도 좋으나, salt값은 조회,수정 하면안된다. 만약 참고할 일이있으면 정수현에게 다렉을 보내도록하자.
            if ( jwt.isValid(token,0) ==0 ) {
                Map<String, Object> payloads = jwt.validateFormat(token, 0);
                Long id = Long.valueOf(String.valueOf(payloads.get("id")));
                return userMapper.getMe(id);
            }
            else{
                throw new AccessTokenInvalidException(ErrorMessage.ACCESS_FORBIDDEN_AUTH_INVALID_EXCEPTION);
            }
        }
    }

    @Override
    public String setProfile(MultipartFile multipartFile) throws Exception{
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String token = request.getHeader(accessTokenName);
        if ( token == null){
            return "로그인을 해주세요";
        }
        else {
            // user id로 User를 select 하는것은 자유롭게 해도 좋으나, salt값은 조회,수정 하면안된다. 만약 참고할 일이있으면 정수현에게 다렉을 보내도록하자.
            if ( jwt.isValid(token,0) ==0 ) {
                Map<String, Object> payloads = jwt.validateFormat(token, 0);
                Long id = Long.valueOf(String.valueOf(payloads.get("id")));
                String url = s3Util.uploadObject(multipartFile);
                userMapper.setProfile(id, url);
            }
            else{
                throw new AccessTokenInvalidException(ErrorMessage.ACCESS_FORBIDDEN_AUTH_INVALID_EXCEPTION);
            }
        }
        return "프로필 사진이 설정되었습니다";
    }
}
