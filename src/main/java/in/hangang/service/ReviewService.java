package in.hangang.service;

import in.hangang.criteria.Criteria;
import in.hangang.domain.Review;

import java.util.ArrayList;
import java.util.HashMap;

public interface ReviewService {
    ArrayList<Review> getReviewList(Criteria criteria) throws Exception;
    ArrayList<Review> getReviewListByUserId() throws Exception;
    Review getReview(Long id) throws Exception;
    ArrayList<Review> getReviewByLectureId(Long id, Criteria criteria) throws Exception;
    void createReview(Review review) throws Exception;
    void likesReview(Long id) throws Exception;
    ArrayList<String> getSemesterDateByLectureId(Long id) throws Exception;
    ArrayList<HashMap<String, String>> getClassByLectureId(Long id) throws Exception;
    void scrapReview(Review review) throws Exception;
    ArrayList<Review> getScrapReviewList() throws Exception;
    void deleteScrapReview(Review review) throws Exception;
    Long getCountScrapReview() throws Exception;

}
