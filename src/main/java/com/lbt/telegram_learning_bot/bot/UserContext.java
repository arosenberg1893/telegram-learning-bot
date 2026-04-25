package com.lbt.telegram_learning_bot.bot;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Корневой контекст сессии пользователя.
 *
 * <p>Агрегирует три специализированных контекста:</p>
 * <ul>
 *   <li>{@link NavigationContext} — текущая позиция в иерархии курсов, история страниц</li>
 *   <li>{@link TestContext}       — состояние активного теста</li>
 *   <li>{@link AdminEditContext}  — состояние редактирования контента администратором</li>
 * </ul>
 *
 * <p>Поля {@code userName} и {@code currentPlatformUserId} остаются на корневом уровне,
 * так как используются во всех трёх сценариях.</p>
 *
 * <p>Все делегирующие методы сохранены для обратной совместимости — вызывающий код
 * менять не нужно. При рефакторинге отдельных хендлеров можно постепенно переходить
 * на прямой доступ через {@code context.nav()}, {@code context.test()},
 * {@code context.admin()}.</p>
 */
@Data
public class UserContext {

    private String userName;
    private Long currentPlatformUserId;

    private NavigationContext nav = new NavigationContext();
    private TestContext test = new TestContext();
    private AdminEditContext admin = new AdminEditContext();

    // ======================== NavigationContext delegation ========================

    public Long getCurrentCourseId()                    { return nav.getCurrentCourseId(); }
    public void setCurrentCourseId(Long v)              { nav.setCurrentCourseId(v); }

    public Long getCurrentSectionId()                   { return nav.getCurrentSectionId(); }
    public void setCurrentSectionId(Long v)             { nav.setCurrentSectionId(v); }

    public Long getCurrentTopicId()                     { return nav.getCurrentTopicId(); }
    public void setCurrentTopicId(Long v)               { nav.setCurrentTopicId(v); }

    public Long getCurrentBlockId()                     { return nav.getCurrentBlockId(); }
    public void setCurrentBlockId(Long v)               { nav.setCurrentBlockId(v); }

    public List<Long> getCurrentTopicBlockIds()         { return nav.getCurrentTopicBlockIds(); }
    public void setCurrentTopicBlockIds(List<Long> v)   { nav.setCurrentTopicBlockIds(v); }

    public int getCurrentBlockIndex()                   { return nav.getCurrentBlockIndex(); }
    public void setCurrentBlockIndex(int v)             { nav.setCurrentBlockIndex(v); }

    public List<Long> getCurrentBlockQuestionIds()      { return nav.getCurrentBlockQuestionIds(); }
    public void setCurrentBlockQuestionIds(List<Long> v){ nav.setCurrentBlockQuestionIds(v); }

    public int getCurrentBlockQuestionIndex()           { return nav.getCurrentBlockQuestionIndex(); }
    public void setCurrentBlockQuestionIndex(int v)     { nav.setCurrentBlockQuestionIndex(v); }

    public Integer getCurrentPage()                     { return nav.getCurrentPage(); }
    public void setCurrentPage(Integer v)               { nav.setCurrentPage(v); }

    public Integer getPreviousSectionPage()             { return nav.getPreviousSectionPage(); }
    public void setPreviousSectionPage(Integer v)       { nav.setPreviousSectionPage(v); }

    public Integer getPreviousTopicPage()               { return nav.getPreviousTopicPage(); }
    public void setPreviousTopicPage(Integer v)         { nav.setPreviousTopicPage(v); }

    public Integer getPreviousCoursesPage()             { return nav.getPreviousCoursesPage(); }
    public void setPreviousCoursesPage(Integer v)       { nav.setPreviousCoursesPage(v); }

    public String getCoursesListSource()                { return nav.getCoursesListSource(); }
    public void setCoursesListSource(String v)          { nav.setCoursesListSource(v); }

    public String getSearchQuery()                      { return nav.getSearchQuery(); }
    public void setSearchQuery(String v)                { nav.setSearchQuery(v); }

    public String getPreviousMenuState()                { return nav.getPreviousMenuState(); }
    public void setPreviousMenuState(String v)          { nav.setPreviousMenuState(v); }

    public Integer getLastInteractiveMessageId()        { return nav.getLastInteractiveMessageId(); }
    public void setLastInteractiveMessageId(Integer v)  { nav.setLastInteractiveMessageId(v); }

    public List<Integer> getLastMediaMessageIds()       { return nav.getLastMediaMessageIds(); }
    public void setLastMediaMessageIds(List<Integer> v) { nav.setLastMediaMessageIds(v); }

    // ======================== TestContext delegation ========================

    public boolean isTestMode()                         { return test.isTestMode(); }
    public void setTestMode(boolean v)                  { test.setTestMode(v); }

    public String getTestType()                         { return test.getTestType(); }
    public void setTestType(String v)                   { test.setTestType(v); }

    public List<Long> getTestQuestionIds()              { return test.getTestQuestionIds(); }
    public void setTestQuestionIds(List<Long> v)        { test.setTestQuestionIds(v); }

    public int getCurrentTestQuestionIndex()            { return test.getCurrentTestQuestionIndex(); }
    public void setCurrentTestQuestionIndex(int v)      { test.setCurrentTestQuestionIndex(v); }

    public int getCorrectAnswers()                      { return test.getCorrectAnswers(); }
    public void setCorrectAnswers(int v)                { test.setCorrectAnswers(v); }

    public int getWrongAnswers()                        { return test.getWrongAnswers(); }
    public void setWrongAnswers(int v)                  { test.setWrongAnswers(v); }

    // ======================== AdminEditContext delegation ========================

    public Long getEditingCourseId()                    { return admin.getEditingCourseId(); }
    public void setEditingCourseId(Long v)              { admin.setEditingCourseId(v); }

    public Long getEditingSectionId()                   { return admin.getEditingSectionId(); }
    public void setEditingSectionId(Long v)             { admin.setEditingSectionId(v); }

    public Long getEditingTopicId()                     { return admin.getEditingTopicId(); }
    public void setEditingTopicId(Long v)               { admin.setEditingTopicId(v); }

    public List<PendingImage> getPendingImages()        { return admin.getPendingImages(); }
    public void setPendingImages(List<PendingImage> v)  { admin.setPendingImages(v); }

    public List<String> getPendingImageDescriptions()   { return admin.getPendingImageDescriptions(); }
    public void setPendingImageDescriptions(List<String> v){ admin.setPendingImageDescriptions(v); }

    public int getCurrentImageIndex()                   { return admin.getCurrentImageIndex(); }
    public void setCurrentImageIndex(int v)             { admin.setCurrentImageIndex(v); }

    public Long getTargetEntityId()                     { return admin.getTargetEntityId(); }
    public void setTargetEntityId(Long v)               { admin.setTargetEntityId(v); }

    public String getTargetEntityType()                 { return admin.getTargetEntityType(); }
    public void setTargetEntityType(String v)           { admin.setTargetEntityType(v); }

    public Integer getAdminSectionsPage()               { return admin.getAdminSectionsPage(); }
    public void setAdminSectionsPage(Integer v)         { admin.setAdminSectionsPage(v); }

    public Integer getAdminTopicsPage()                 { return admin.getAdminTopicsPage(); }
    public void setAdminTopicsPage(Integer v)           { admin.setAdminTopicsPage(v); }

    public String getSelectedBackupFileName()           { return admin.getSelectedBackupFileName(); }
    public void setSelectedBackupFileName(String v)     { admin.setSelectedBackupFileName(v); }
}