package com.tradepass.service;

import com.tradepass.config.OssProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyBlobMigrationRunnerTest {

    @Test
    void skipsUnlessOssAndMigrationSwitchAreBothEnabled() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        OssProperties properties = new OssProperties();
        LegacyBlobMigrationRunner runner = new LegacyBlobMigrationRunner(
                jdbc, storage, mock(ContractArchiveService.class), properties);

        runner.run(new DefaultApplicationArguments(new String[0]));
        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(Object[].class));

        when(storage.isEnabled()).thenReturn(true);
        runner.run(new DefaultApplicationArguments(new String[0]));
        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void safelyCompletesWhenThereIsNoLegacyData() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        when(storage.isEnabled()).thenReturn(true);
        doReturn(List.of()).when(jdbc)
                .query(anyString(), any(RowMapper.class), any(Object[].class));
        OssProperties properties = new OssProperties();
        properties.setMigrateLegacyBlobs(true);
        LegacyBlobMigrationRunner runner = new LegacyBlobMigrationRunner(
                jdbc, storage, mock(ContractArchiveService.class), properties);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbc, atLeastOnce()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }
}
