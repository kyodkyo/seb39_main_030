package debateProgram.server.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import debateProgram.server.comments.repository.CommentsRepository;
import debateProgram.server.declaration.repository.DeclarationRepository;
import debateProgram.server.discussion.repository.DiscussionRepository;
import debateProgram.server.exception.BusinessLogicException;
import debateProgram.server.exception.ExceptionCode;
import debateProgram.server.guestbook.repository.GuestbookRepository;
import debateProgram.server.questions.repository.QuestionsRepository;
import debateProgram.server.user.recommend.Recommend;
import debateProgram.server.user.entity.User;
import debateProgram.server.user.model.*;
import debateProgram.server.user.model.AllListsInterface.CommentsDto;
import debateProgram.server.user.model.AllListsInterface.DeclarationsDto;
import debateProgram.server.user.model.AllListsInterface.DiscussionsDto;
import debateProgram.server.user.model.AllListsInterface.QuestionsDto;
import debateProgram.server.user.recommend.RecommendRepository;
import debateProgram.server.user.repository.UserRepository;
import debateProgram.server.user.tags.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Component
@RequiredArgsConstructor
public class UserService {

    @Value("${KAKAO_Client_Id}")
    private String client_id;

    @Value("${KAKAO_Client_Secret}")
    private String client_secret;

    private final UserRepository userRepository;

    private final DiscussionRepository discussionRepository;

    private final CommentsRepository commentsRepository;

    private final QuestionsRepository questionsRepository;

    private final DeclarationRepository declarationRepository;

    private final GuestbookRepository guestbookRepository;

    private final RecommendRepository recommendRepository;

    private final TagsRepository tagsRepository;


    public OauthToken getAccessToken(String code) {

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", client_id);
        params.add("redirect_uri", "https://team30.vercel.app/auth");
        params.add("code", code);
        params.add("client_secret", client_secret);

        HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(params, headers);

        ResponseEntity<String> accessTokenResponse = restTemplate.exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST,
                kakaoTokenRequest,
                String.class
        );

        ObjectMapper objectMapper = new ObjectMapper();
        OauthToken oauthToken = null;

        try {
            oauthToken = objectMapper.readValue(accessTokenResponse.getBody(), OauthToken.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return oauthToken;
    }


    public OauthToken getAccessTokenTest(String testCode) {

        RestTemplate restTemplateTest = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", client_id);
        params.add("redirect_uri", "http://localhost:3000/auth");
        params.add("code", testCode);
        params.add("client_secret", client_secret);

        HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(params, headers);

        ResponseEntity<String> accessTokenResponse = restTemplateTest.exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST,
                kakaoTokenRequest,
                String.class
        );

        ObjectMapper objectMapper = new ObjectMapper();
        OauthToken oauthToken = null;

        try {
            oauthToken = objectMapper.readValue(accessTokenResponse.getBody(), OauthToken.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return oauthToken;
    }


    public User saveUserAndGetUser(String token) {

        KakaoProfile profile = findProfile(token);

        User user = userRepository.findByKakaoId(profile.getId());

        if (user == null) {
            user = User.builder()
                    .kakaoId(profile.getId())
                    .profileImg(profile.getKakao_account().getProfile().getProfile_image_url())
                    .nickname(profile.getKakao_account().getProfile().getNickname())
                    .kakaoEmail(profile.getKakao_account().getEmail())
                    .userRole("ROLE_USER")
                    .userState("Y")
                    .build();
            userRepository.save(user);
        }
        else {
            userRepository.updateProfile(user.getUserCode(),
                    profile.getKakao_account().getProfile().getProfile_image_url(),
                    profile.getKakao_account().getEmail());
            userRepository.updateUserState(user.getUserCode(), "Y");
        }

        return user;
    }


    public KakaoProfile findProfile(String token) {

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        HttpEntity<MultiValueMap<String, String>> kakaoProfileRequest = new HttpEntity<>(headers);

        ResponseEntity<String> kakaoProfileResponse = restTemplate.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.POST,
                kakaoProfileRequest,
                String.class
        );

        ObjectMapper objectMapper = new ObjectMapper();
        KakaoProfile kakaoProfile = null;

        try {
            kakaoProfile = objectMapper.readValue(kakaoProfileResponse.getBody(), KakaoProfile.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return kakaoProfile;
    }


    public User logoutUser(int userCode) {
        userRepository.updateUserState(userCode, "N");
        User findUser = findVerifiedUser(userCode);

        return findUser;
    }


    @Transactional(readOnly = true)
    public User findVerifiedUser(int userCode) {
        Optional<User> optionalMember = userRepository.findById(userCode);

        User findUser = optionalMember.orElseThrow(
                () -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));

        return findUser;
    }

    public UpdateUserResponseDto findUserInfo(int userCode) {
        User user = findVerifiedUser(userCode);
        UserTags tagList = tagsRepository.findByUserCode(userCode);

        UpdateUserResponseDto dto = UpdateUserResponseDto.builder()
                .userCode(user.getUserCode())
                .userState(user.getUserState())
                .discussionState(user.getDiscussionState())
                .kakaoId(user.getKakaoId())
                .nickname(user.getNickname())
                .profileImg(user.getProfileImg())
                .kakaoEmail(user.getKakaoEmail())
                .userLikes(user.getUserLikes())
                .socketId(user.getSocketId())
                .tags(tagList)
                .build();
        return dto;
    }

    public UpdateUserResponseDto updateUserInfo(UpdateUserRequestDto dto) {
        userRepository.updateInfo(dto.getUserCode(), dto.getNickname(), dto.getProfileImg(), dto.getKakaoEmail());
        if(tagsRepository.findByUserCode(dto.getUserCode()) == null){
            tagsRepository.saveTags(dto.getUserCode(), dto.getTag1(), dto.getTag2(), dto.getTag3());
        } else {
            tagsRepository.updateTags(dto.getUserCode(), dto.getTag1(), dto.getTag2(), dto.getTag3());
        }

        UpdateUserResponseDto userInfo = findUserInfo(dto.getUserCode());

        return userInfo;
    }


    public User updateDiscussionState(int userCode, String state) {
        userRepository.updateLiveState(userCode, state);
        User findUser = findVerifiedUser(userCode);

        return findUser;
    }


    @Transactional
    public int updateUserLikes(int userCode, int identifier) {
        User user = findVerifiedUser(userCode);
        int likes = user.getUserLikes();

        if (identifier == 1) {
            likes = likes + 1;
            userRepository.updateUserLikes(userCode, likes);
        } else if (identifier == 0) {
            likes = likes - 1;
            userRepository.updateUserLikes(userCode, likes);
        }

        return likes;
    }


    public String verifyEmailAndDeleteAll(int userCode, String email) {
        User user = findVerifiedUser(userCode);
        String result = "";

        /**
         * 이메일 검증 로직
         */


        if (user.getKakaoEmail().equals(email)) {
            guestbookRepository.deleteAllByUserCode(user.getUserCode());
            questionsRepository.deleteAllByUserCode(user.getUserCode());
            declarationRepository.deleteAllByUserCode(user.getUserCode());
            commentsRepository.deleteAllByUserCode(user.getUserCode());
            discussionRepository.deleteAllByUserCode(user.getUserCode());
            userRepository.delete(user);
            result = "SUCCESS";
        } else {
            result = "FAIL";
        }

        return result;
    }


    public AllListsResponseDto findAllLists(int userCode) {
        User user = findVerifiedUser(userCode);

        List<DiscussionsDto> discussionList = userRepository.findAllDiscussions(userCode);
        List<CommentsDto> commentList = userRepository.findAllComments(userCode);
        List<QuestionsDto> questionList = userRepository.findAllQuestions(userCode);
        List<DeclarationsDto> declarationList = userRepository.findAllDeclarations(userCode);

        AllListsResponseDto dto = AllListsResponseDto.builder()
                .userCode(user.getUserCode())
                .nickname(user.getNickname())
                .profileImg(user.getProfileImg())
                .discussionLists(discussionList)
                .commentLists(commentList)
                .questionLists(questionList)
                .declarationLists(declarationList)
                .build();

        return dto;
    }

    public User registerSocketId(int userCode, String socketId) {
        userRepository.updateSocketId(userCode, socketId);
        User user = findVerifiedUser(userCode);
        return user;
    }

    @Transactional(readOnly = true)
    public Recommend findVerifiedRecommend(int userCode, int targetCode) {
        Optional<Recommend> optionalRecommend = recommendRepository.findByUserCodeAndTargetCode(userCode, targetCode);
        Recommend recommend = optionalRecommend.orElse(null);

        return recommend;
    }

    public Recommend findLikesHistory(int userCode, int targetCode) {
        Optional<Recommend> optionalRecommend = recommendRepository.findByUserCodeAndTargetCode(userCode, targetCode);
        Recommend findRecommend = optionalRecommend.orElseGet(Recommend::new);

        return findRecommend;
    }

    public void saveUserRecommend(Recommend recommend) {
        recommendRepository.save(recommend);
    }

    public void updateUserRecommend(Recommend recommend) {
        int userCode = recommend.getUserCode();
        int targetCode = recommend.getTargetCode();
        String state = recommend.getLikes();
        recommendRepository.updateLikesState(userCode, targetCode, state);
    }

}
