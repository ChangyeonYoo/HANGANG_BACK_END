package in.hangang.mapper;

import in.hangang.domain.LectureTimeTable;
import in.hangang.domain.UserTimeTable;

import java.util.ArrayList;

public interface TimetableMapper {
    ArrayList<UserTimeTable> getTableListByUserId(Long userId, Long semesterDateId);
    void createTimetable(Long user_id, Long semester_date_id, String name);
    void updateTimeTable(Long timeTableId, String name);
    void deleteTimetable(Long timeTableId);
    void createLectureOnTimeTable(Long timeTableId, Long lectureId);
    void deleteLectureOnTimeTable(Long timeTableId, Long lectureId);
    ArrayList<String> getClassTimeByLectureId(Long lectureId);
    Long getSemesterDateByLectureId(Long lectureId);
    Long getSemesterDateByTimeTableId(Long timeTableId);
    Long getCountSemesterDate(Long userId, Long semesterDateId);
    Long getCountTimeTable(Long userId);
    Long isAlreadyExists(Long timeTableId, Long lectureId);
    Long isExists(Long lectureId);
    Long getUserIdByTimeTableId(Long timeTableId);
    ArrayList<LectureTimeTable> getLectureListByTimeTableId(Long timeTableId);
    String getNameByTimeTableId(Long timeTableId);
    ArrayList<String> getClassTimeByTimeTable(Long timeTableId);
    Long getSemesterDateId(Long semesterDateId);
    Long getLectureIdByCode(String code);
    Long createLecture(LectureTimeTable lectureTimeTable);
}
