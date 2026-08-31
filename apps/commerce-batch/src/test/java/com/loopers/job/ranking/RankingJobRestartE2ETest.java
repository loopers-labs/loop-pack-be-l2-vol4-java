package com.loopers.job.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
    "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
class RankingJobRestartE2ETest {
    private static final String SOURCE_SHA = "d80a24248fd42f959bddc497efa65afd0d3f1cab420933800140b3027bac98e1";
    private static final String RESULT_SHA = "cd34a2cb816c288cd704e7fdf27784c01b642b571288ec4f1a854b7a1a4a48e4";

    @Autowired private JobLauncherTestUtils launcher;
    @Autowired private JobRepositoryTestUtils repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired @Qualifier(WeeklyRankingJobConfig.JOB_NAME) private Job job;

    @BeforeEach
    void seedPublicFixture() {
        repository.removeJobExecutions();
        launcher.setJob(job);
        jdbc.execute("drop table if exists weekly_ranking");
        jdbc.execute("drop table if exists ranking_applied_event");
        jdbc.execute("drop table if exists ranking_source");
        jdbc.execute("create table ranking_source (seq bigint primary key, event_id varchar(80) not null, product_id bigint not null, score_delta bigint not null, occurred_at timestamp not null)");
        jdbc.execute("create table ranking_applied_event (snapshot_id varchar(40) not null, event_id varchar(80) not null, primary key(snapshot_id,event_id))");
        jdbc.execute("create table weekly_ranking (snapshot_id varchar(40) not null, product_id bigint not null, score bigint not null, ranking_position int not null, primary key(snapshot_id,product_id))");
        jdbc.update("insert into ranking_source values (1,'event-01',101,100,'2026-08-17 00:00:00'),(2,'event-02',202,70,'2026-08-18 00:00:00'),(3,'event-02',202,70,'2026-08-18 00:00:00')");
    }

    @Test
    void failedExecutionRestartsSameInstanceFromCheckpointTwoAndMatchesChecksums() throws Exception {
        JobExecution failed = launcher.launchJob(parameters("2026-08-17", true));
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(failed.getStepExecutions()).singleElement().satisfies(step -> {
            assertThat(step.getExecutionContext().getInt("reader.index")).isEqualTo(2);
            assertThat(step.getWriteCount()).isEqualTo(2);
        });

        JobExecution restarted = launcher.launchJob(parameters("2026-08-17", false));
        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstance().getInstanceId()).isEqualTo(failed.getJobInstance().getInstanceId());
        assertThat(restarted.getId()).isNotEqualTo(failed.getId());
        assertThat(restarted.getStepExecutions()).singleElement().satisfies(step ->
            assertThat(step.getExecutionContext().getInt("reader.index")).isEqualTo(3)
        );

        assertThat(checksum(sourceRows())).isEqualTo(SOURCE_SHA);
        assertThat(resultRows()).isEqualTo("snapshot-2026w34\t101\t100\t1\nsnapshot-2026w34\t202\t70\t2\n");
        assertThat(checksum(resultRows())).isEqualTo(RESULT_SHA);
        assertThat(jdbc.queryForObject("select count(*) from ranking_applied_event", Integer.class)).isEqualTo(2);
    }

    @Test
    void changedIdentifyingParameterCreatesFreshInstanceWithSameResult() throws Exception {
        JobExecution first = launcher.launchJob(parameters("2026-08-17", false));
        jdbc.update("delete from weekly_ranking");
        jdbc.update("delete from ranking_applied_event");
        JobExecution fresh = launcher.launchJob(parameters("2026-08-18", false));
        assertThat(fresh.getJobInstance().getInstanceId()).isNotEqualTo(first.getJobInstance().getInstanceId());
        assertThat(checksum(resultRows())).isEqualTo(RESULT_SHA);
    }

    private org.springframework.batch.core.JobParameters parameters(String periodStart, boolean injectFailure) {
        return new JobParametersBuilder()
            .addString("periodStart", periodStart, true)
            .addString("injectFailure", Boolean.toString(injectFailure), false)
            .toJobParameters();
    }

    private String sourceRows() {
        return jdbc.query("select seq,event_id,product_id,score_delta,occurred_at from ranking_source order by seq",
            rs -> {
                StringBuilder out = new StringBuilder();
                while (rs.next()) {
                    out.append(rs.getLong(1)).append('\t').append(rs.getString(2)).append('\t')
                        .append(rs.getLong(3)).append('\t').append(rs.getLong(4)).append('\t')
                        .append(rs.getTimestamp(5).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))).append('\n');
                }
                return out.toString();
            });
    }

    private String resultRows() {
        return jdbc.query("select snapshot_id,product_id,score,ranking_position from weekly_ranking order by ranking_position",
            rs -> {
                StringBuilder out = new StringBuilder();
                while (rs.next()) {
                    out.append(rs.getString(1)).append('\t').append(rs.getLong(2)).append('\t')
                        .append(rs.getLong(3)).append('\t').append(rs.getInt(4)).append('\n');
                }
                return out.toString();
            });
    }

    private String checksum(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
