package com.thiago.gitpublish.git;

import java.util.List;
import java.util.Optional;

public interface GitClient {
    void ensureRepository();
    String currentBranch();
    String repositoryName();
    String currentCommit();
    void ensureCleanWorkingTree();
    void fetchBranchAndTags(String branch);
    void ensureBranchSynchronized(String branch);
    boolean localTagExists(String tag);
    boolean remoteTagExists(String tag);
    void createAnnotatedTag(String tag, String message);
    void pushTag(String tag);
    void deleteLocalTag(String tag);
    List<String> listTags();
    Optional<String> latestReachableTag();
    List<String> commitSubjectsSince(String tag);
    void addFile(String path);
    void commit(String message);
    void pushCurrentBranch(String branch);
}
