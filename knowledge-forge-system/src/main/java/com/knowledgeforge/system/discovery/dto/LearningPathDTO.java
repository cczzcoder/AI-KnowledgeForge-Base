package com.knowledgeforge.system.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningPathDTO {
    private String topic;
    private String summary;
    private List<LearningStepDTO> steps;
    private LearningExampleDTO example;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningStepDTO {
        private int order;
        private String title;
        private String description;
        private String status;
        private List<String> knowledgePoints;
        private List<String> recommendedResources;
        private List<String> expectedOutcomes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningExampleDTO {
        private String topic;
        private String overallGoal;
        private List<ExampleStepDTO> exampleSteps;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ExampleStepDTO {
            private int stage;
            private String stageName;
            private String objective;
            private List<String> coreContents;
            private String duration;
            private String outcome;
        }
    }
}