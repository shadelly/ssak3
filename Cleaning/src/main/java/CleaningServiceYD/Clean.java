package CleaningServiceYD;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PostPersist;
import javax.persistence.Table;

import org.springframework.beans.BeanUtils;

@Entity
@Table(name="Clean_table")
public class Clean {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String status;
    private Long requestId;
    private String cleanDate;

    @PostPersist
    public void onPostPersist(){
        CleaningConfirmed cleaningConfirmed = new CleaningConfirmed();

        cleaningConfirmed.setRequestId(getRequestId());
        cleaningConfirmed.setStatus("cleaningCompleted");
        cleaningConfirmed.setCleanDate(getCleanDate());

        BeanUtils.copyProperties(this, cleaningConfirmed);
        cleaningConfirmed.publishAfterCommit();

    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }
    public String getCleanDate() {
        return cleanDate;
    }

    public void setCleanDate(String cleanDate) {
        this.cleanDate = cleanDate;
    }




}
