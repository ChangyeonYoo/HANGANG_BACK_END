package in.hangang.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.util.ArrayList;


public class LectureBank {
    private Long id;
    private Long user_id;
    private Long lecture_id;
    private ArrayList<String> category;
    private String title;
    private String content;
    private Integer point_price;

    @ApiModelProperty(hidden = true)
    private Long hits;
    @ApiModelProperty(hidden = true)
    private Timestamp created_at;
    @ApiModelProperty(hidden = true)
    private Timestamp updated_at;

    @ApiModelProperty(hidden = true)
    private User user;
    @ApiModelProperty(hidden = true)
    private Lecture lecture;



    public Long getId() {
        return id;
    }

    public Long getUser_id() {
        return user_id;
    }



    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Integer getPoint_price() {
        return point_price;
    }

    public Long getHits() {
        return hits;
    }

    public Timestamp getCreated_at() {
        return created_at;
    }

    public Timestamp getUpdated_at() {
        return updated_at;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setUser_id(Long user_id) {
        this.user_id = user_id;
    }



    public void setTitle(String title) {
        this.title = title;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setPoint_price(Integer point_price) {
        this.point_price = point_price;
    }

    public void setHits(Long hits) {
        this.hits = hits;
    }

    public void setCreated_at(Timestamp created_at) {
        this.created_at = created_at;
    }

    public void setUpdated_at(Timestamp updated_at) {
        this.updated_at = updated_at;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }


    public Long getLecture_id() {
        return lecture_id;
    }


    public void setLecture_id(Long lecture_id) {
        this.lecture_id = lecture_id;
    }

    public ArrayList<String> getCategory() {
        return category;
    }

    public void setCategory(ArrayList<String> category) {
        this.category = category;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }
}
