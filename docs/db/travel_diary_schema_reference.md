-- MySQL dump 10.13  Distrib 8.0.41, for macos15 (arm64)
--
-- Host: 127.0.0.1    Database: mydb
-- ------------------------------------------------------
-- Server version	9.2.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `accommodation_amenities`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `accommodation_amenities` (
  `accommodation_id` bigint NOT NULL,
  `amenity_id` int NOT NULL,
  PRIMARY KEY (`accommodation_id`,`amenity_id`),
  KEY `amenity_id` (`amenity_id`),
  CONSTRAINT `accommodation_amenities_ibfk_1` FOREIGN KEY (`accommodation_id`) REFERENCES `accommodation_info` (`destination_id`) ON DELETE CASCADE,
  CONSTRAINT `accommodation_amenities_ibfk_2` FOREIGN KEY (`amenity_id`) REFERENCES `amenities` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `accommodation_info`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `accommodation_info` (
  `destination_id` bigint NOT NULL,
  `checkin_time` varchar(10) DEFAULT NULL,
  `checkout_time` varchar(10) DEFAULT NULL,
  `room_count` int DEFAULT NULL,
  `room_type` varchar(255) DEFAULT NULL,
  `star_rating` decimal(2,1) DEFAULT NULL,
  `breakfast_included` tinyint(1) DEFAULT NULL,
  `parking_available` tinyint(1) DEFAULT NULL,
  `pet_allowed` tinyint(1) DEFAULT NULL,
  `contact_number` varchar(32) DEFAULT NULL,
  `homepage_url` varchar(255) DEFAULT NULL,
  `etc` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`destination_id`),
  CONSTRAINT `fk_accommodation_dest_id` FOREIGN KEY (`destination_id`) REFERENCES `destinations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `activity_amenities`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_amenities` (
  `activity_id` bigint NOT NULL,
  `amenity_id` int NOT NULL,
  PRIMARY KEY (`activity_id`,`amenity_id`),
  KEY `amenity_id` (`amenity_id`),
  CONSTRAINT `activity_amenities_ibfk_1` FOREIGN KEY (`activity_id`) REFERENCES `activity_info` (`destination_id`) ON DELETE CASCADE,
  CONSTRAINT `activity_amenities_ibfk_2` FOREIGN KEY (`amenity_id`) REFERENCES `amenities` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `activity_info`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_info` (
  `destination_id` bigint NOT NULL,
  `opening_hours` varchar(1000) DEFAULT NULL,
  `required_time` varchar(32) DEFAULT NULL,
  `admission_fee` text DEFAULT NULL,
  `age_limit` varchar(32) DEFAULT NULL,
  `reservation` tinyint(1) DEFAULT NULL,
  `equipment_included` tinyint(1) DEFAULT NULL,
  `parking_available` tinyint(1) DEFAULT NULL,
  `contact_number` varchar(255) DEFAULT NULL,
  `homepage_url` varchar(255) DEFAULT NULL,
  `guide` text DEFAULT NULL,
  PRIMARY KEY (`destination_id`),
  CONSTRAINT `fk_activity_dest_id` FOREIGN KEY (`destination_id`) REFERENCES `destinations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `amenities`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `amenities` (
  `id` int NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code_UNIQUE` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `amenity_destination_types`
--
-- 편의시설이 어떤 여행지 유형에 적용 가능한지 정의하는 마스터 N:M 매핑이다.
-- 실제 여행지별 선택 값은 `*_amenities` 테이블이 따로 저장한다.
-- `destination_type`은 DestinationType enum 값을 그대로 쓴다.
-- (ATTRACTION / RESTAURANTS / CAFE / ACCOMMODATION / ACTIVITY / SHOP)

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `amenity_destination_types` (
  `amenity_id` int NOT NULL,
  `destination_type` varchar(30) NOT NULL,
  PRIMARY KEY (`amenity_id`,`destination_type`),
  KEY `idx_amenity_destination_types_type` (`destination_type`,`amenity_id`),
  CONSTRAINT `fk_amenity_destination_types_amenity` FOREIGN KEY (`amenity_id`) REFERENCES `amenities` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `amenity_translations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `amenity_translations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `amenity_id` int NOT NULL,
  `language_code` varchar(5) NOT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `amenity_id` (`amenity_id`,`language_code`),
  CONSTRAINT `amenity_translations_ibfk_1` FOREIGN KEY (`amenity_id`) REFERENCES `amenities` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=115 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `attraction_amenities`
--


/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attraction_amenities` (
  `attraction_id` bigint NOT NULL,
  `amenity_id` int NOT NULL,
  PRIMARY KEY (`attraction_id`,`amenity_id`),
  KEY `amenity_id` (`amenity_id`),
  CONSTRAINT `attraction_amenities_ibfk_1` FOREIGN KEY (`attraction_id`) REFERENCES `attraction_info` (`destination_id`) ON DELETE CASCADE,
  CONSTRAINT `attraction_amenities_ibfk_2` FOREIGN KEY (`amenity_id`) REFERENCES `amenities` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `attraction_info`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attraction_info` (
  `destination_id` bigint NOT NULL,
  `closed_days` varchar(500) DEFAULT NULL,
  `opening_hours` varchar(1000) DEFAULT NULL,
  `admission_fee` text DEFAULT NULL,
  `parking_available` tinyint(1) DEFAULT NULL,
  `contact_number` varchar(255) DEFAULT NULL,
  `homepage_url` varchar(255) DEFAULT NULL,
  `guide` text DEFAULT NULL,
  PRIMARY KEY (`destination_id`),
  CONSTRAINT `fk_attraction_dest_id` FOREIGN KEY (`destination_id`) REFERENCES `destinations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `blocked_emails`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blocked_emails` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email_hash` char(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT 'SHA-256(정규화 이메일 + 서버 pepper), 원본 미보관',
  `user_id` bigint DEFAULT NULL COMMENT '참고용(익명화 후에도 id는 유지)',
  `sanction_id` bigint DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `released_at` datetime DEFAULT NULL,
  `released_by` bigint DEFAULT NULL,
  `active_email_hash` char(64) CHARACTER SET ascii COLLATE ascii_general_ci GENERATED ALWAYS AS ((case when (`released_at` is null) then `email_hash` end)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_blocked_emails_active` (`active_email_hash`),
  KEY `idx_blocked_emails_sanction` (`sanction_id`),
  KEY `idx_blocked_emails_user` (`user_id`),
  KEY `fk_blocked_emails_admin` (`created_by`),
  KEY `fk_blocked_emails_release` (`released_by`),
  CONSTRAINT `fk_blocked_emails_admin` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_blocked_emails_release` FOREIGN KEY (`released_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_blocked_emails_sanction` FOREIGN KEY (`sanction_id`) REFERENCES `user_sanctions` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_blocked_emails_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `bookmarks`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bookmarks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `user_id` bigint NOT NULL,
  `target_id` bigint NOT NULL,
  `target_type` varchar(20) NOT NULL DEFAULT 'DESTINATION',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_bookmarks_user_type_target` (`user_id`,`target_type`,`target_id`),
  KEY `fk_bookmark_users1_idx` (`user_id`),
  CONSTRAINT `bookmark_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=55 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `categories`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name_UNIQUE` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `category_destination_types`
--
-- 카테고리가 어떤 여행지 유형에 적용 가능한지 정의하는 마스터 N:M 매핑이다.
-- 실제 여행지별 선택 값은 `destination_categories` 가 따로 저장한다.
-- `destination_type`은 DestinationType enum 값을 그대로 쓴다.
-- (ATTRACTION / RESTAURANTS / CAFE / ACCOMMODATION / ACTIVITY / SHOP)

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category_destination_types` (
  `category_id` bigint NOT NULL,
  `destination_type` varchar(30) NOT NULL,
  PRIMARY KEY (`category_id`,`destination_type`),
  KEY `idx_category_destination_types_type` (`destination_type`,`category_id`),
  CONSTRAINT `fk_category_destination_types_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `comment_likes`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comment_likes` (
  `user_id` bigint NOT NULL,
  `comment_id` bigint NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`comment_id`),
  KEY `fk_commentlike_coment` (`comment_id`),
  CONSTRAINT `fk_commentlike_coment` FOREIGN KEY (`comment_id`) REFERENCES `destination_comments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_commentlike_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `content_moderations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `content_moderations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(24) NOT NULL COMMENT 'POST | COURSE | POST_COMMENT | COURSE_COMMENT | DESTINATION_COMMENT',
  `target_id` bigint NOT NULL,
  `target_user_id` bigint NOT NULL COMMENT '콘텐츠 작성자',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE(숨김중) | RESTORED',
  `reason` varchar(500) NOT NULL,
  `admin_note` text,
  `created_by` bigint NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `restored_at` datetime DEFAULT NULL,
  `restored_by` bigint DEFAULT NULL,
  `restore_reason` varchar(500) DEFAULT NULL,
  `active_target_type` varchar(24) GENERATED ALWAYS AS ((case when (`status` = 'ACTIVE') then `target_type` end)) VIRTUAL,
  `active_target_id` bigint GENERATED ALWAYS AS ((case when (`status` = 'ACTIVE') then `target_id` end)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_content_moderations_active` (`active_target_type`,`active_target_id`),
  KEY `idx_content_moderations_target` (`target_type`,`target_id`,`created_at`),
  KEY `idx_content_moderations_user` (`target_user_id`,`created_at`),
  KEY `idx_content_moderations_status` (`status`,`created_at`,`id`),
  KEY `fk_content_moderations_admin` (`created_by`),
  KEY `fk_content_moderations_restore` (`restored_by`),
  CONSTRAINT `fk_content_moderations_admin` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_content_moderations_restore` FOREIGN KEY (`restored_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_content_moderations_user` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `country_categories`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `country_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `region_name` varchar(100) NOT NULL,
  `name_en` varchar(100) DEFAULT NULL,
  `icon_path` varchar(255) DEFAULT NULL,
  `code` varchar(20) DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `depth` int DEFAULT '0',
  `subregion` varchar(50) DEFAULT NULL,
  `is_visible` tinyint DEFAULT '1',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=495 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_comment_images`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_comment_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_id` bigint NOT NULL,
  `image_url` varchar(255) NOT NULL,
  `display_order` int NOT NULL DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_course_comment_images_order` (`comment_id`,`display_order`),
  CONSTRAINT `fk_course_comment_images_comment` FOREIGN KEY (`comment_id`) REFERENCES `course_comments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_course_comment_images_order` CHECK (((`display_order` >= 1) and (`display_order` <= 3)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_comment_likes`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_comment_likes` (
  `user_id` bigint NOT NULL,
  `comment_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`comment_id`),
  KEY `fk_comment_likes_comment` (`comment_id`),
  CONSTRAINT `fk_comment_likes_comment` FOREIGN KEY (`comment_id`) REFERENCES `course_comments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comment_likes_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_comments`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_comment_id` bigint DEFAULT NULL,
  `reply_to_comment_id` bigint DEFAULT NULL,
  `content` text NOT NULL,
  `likes` int DEFAULT '0',
  `deleted` tinyint(1) DEFAULT '0',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_course_comments_user` (`user_id`),
  KEY `fk_course_comments_course` (`course_id`),
  KEY `idx_course_comments_parent` (`parent_comment_id`),
  KEY `idx_course_comments_reply_to` (`reply_to_comment_id`),
  CONSTRAINT `fk_course_comments_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_course_comments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_destinations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_destinations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `visit_order` int NOT NULL DEFAULT '1',
  `course_id` bigint NOT NULL,
  `destination_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_course_destination_course1_idx` (`course_id`),
  KEY `fk_course_destination_destinations1_idx` (`destination_id`),
  CONSTRAINT `fk_coursedestinations_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_coursedestinations_destinations` FOREIGN KEY (`destination_id`) REFERENCES `destinations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_images`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `image_url` varchar(255) NOT NULL,
  `uploaded_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_course_images_course` (`course_id`),
  CONSTRAINT `fk_course_images_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `courses`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `courses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `country_id` bigint DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `content` mediumtext,
  `views` int NOT NULL DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` timestamp NULL DEFAULT NULL,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `fk_courses_user` (`user_id`),
  KEY `idx_courses_country_id` (`country_id`),
  CONSTRAINT `fk_courses_country` FOREIGN KEY (`country_id`) REFERENCES `country_categories` (`id`),
  CONSTRAINT `fk_courses_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `destination_categories`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `destination_categories` (
  `category_id` bigint NOT NULL,
  `destination_id` bigint NOT NULL,
  PRIMARY KEY (`category_id`,`destination_id`),
  KEY `fk_destination_category_category1_idx` (`category_id`),
  KEY `fk_destination_category_destinations1_idx` (`destination_id`),
  CONSTRAINT `fk_destinationcategories_categories` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_destinationcategories_destinations` FOREIGN KEY (`destination_id`) REFERENCES `destinations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `destination_comment_images`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `destination_comment_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_id` bigint NOT NULL,
  `image_url` varchar(255) NOT NULL,
  `display_order` int NOT NULL DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_destination_comment_images_order` (`comment_id`,`display_order`),
  CONSTRAINT `fk_destination_comment_images_comment` FOREIGN KEY (`comment_id`) REFERENCES `destination_comments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_destination_comment_images_order` CHECK (((`display_order` >= 1) and (`display_order` <= 3)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `destination_comments`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `destination_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_comment_id` bigint DEFAULT NULL,
  `content` text,
  `likes` int NOT NULL DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` timestamp NULL DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `destination_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_destinations_comment_users1_idx` (`user_id`),
  KEY `fk_destinations_comment_destinations1_idx` (`destination_id`),
  KEY `fk_destinations_comment_parent_idx` (`parent_comment_id`),
  CONSTRAINT `fk_destinationcomments_destination` FOREIGN KEY (`destination_id`) REFERENCES `destinations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_destinationcomments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=86 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `destination_images`
--
-- `image_url`은 서비스 표시 URL이며, 외부 사진을 로컬 저장하면 `/uploads/...`를 사용한다.
-- `source_image_url`은 외부 제공처의 원래 이미지 URL이다.
-- `source_type`은 라이선스가 아닌 이미지 유입 경로이며, 기존 관리자 업로드는 `ADMIN_UPLOAD`이다.
-- 외부 사진의 출처·촬영자·라이선스 metadata는 이미지별로 보존한다.

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `destination_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `image_url` varchar(255) NOT NULL,
  `source_type` varchar(30) NOT NULL DEFAULT 'ADMIN_UPLOAD' COMMENT '이미지 유입 경로: ADMIN_UPLOAD, KTO_PHOTO_GALLERY, KTO_TOURAPI, PUBLIC_KOGL, USER_UPLOAD',
  `source_name` varchar(100) DEFAULT NULL COMMENT '사진 제공기관명 (예: 한국관광공사)',
  `external_content_id` varchar(100) DEFAULT NULL COMMENT '외부 API 콘텐츠 식별자 (예: galContentId)',
  `source_title` varchar(255) DEFAULT NULL COMMENT '외부 원본 사진 제목',
  `photographer` varchar(100) DEFAULT NULL COMMENT '촬영자/저작자',
  `license_type` varchar(50) DEFAULT NULL COMMENT '라이선스 유형 (예: KOGL_TYPE_1)',
  `source_image_url` varchar(1000) DEFAULT NULL COMMENT '외부 API가 제공한 원본 웹 이미지 URL',
  `license_checked_at` datetime DEFAULT NULL COMMENT '해당 라이선스 조건을 확인한 시각',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `is_main` tinyint NOT NULL DEFAULT '0',
  `is_slide` tinyint NOT NULL DEFAULT '0',
  `order_index` int NOT NULL DEFAULT '1',
  `destination_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_destinations_images_destinations1_idx` (`destination_id`),
  CONSTRAINT `fk_destinationimages_destination` FOREIGN KEY (`destination_id`) REFERENCES `destinations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `destination_translations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `destination_translations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `language_code` varchar(10) NOT NULL,
  `name` varchar(255) NOT NULL,
  `description` text,
  `destination_id` bigint NOT NULL,
  `short_description` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_destination_language` (`language_code`,`destination_id`),
  KEY `fk_destination_translations_destinations_idx` (`destination_id`),
  CONSTRAINT `fk_destinationtranslations_destination` FOREIGN KEY (`destination_id`) REFERENCES `destinations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `destinations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `destinations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `latitude` decimal(10,8) DEFAULT NULL,
  `longitude` decimal(11,8) DEFAULT NULL,
  `google_place_id` varchar(255) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `views` int NOT NULL DEFAULT '0',
  `season` enum('SPRING','SUMMER','FALL','WINTER','ALL_SEASONS') NOT NULL,
  `user_id` bigint NOT NULL,
  `region_id` bigint NOT NULL,
  `type` varchar(32) NOT NULL DEFAULT 'ATTRACTION',
  PRIMARY KEY (`id`),
  KEY `fk_destinations_users1_idx` (`user_id`),
  KEY `fk_destinations_country_category2_idx` (`region_id`),
  CONSTRAINT `fk_destinations_region` FOREIGN KEY (`region_id`) REFERENCES `country_categories` (`id`),
  CONSTRAINT `fk_destinations_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- 개인 여행일기 구조: users -> diaries -> diary_pages -> diary_elements
--   * 한 회원이 여러 여행 다이어리를 가질 수 있다.
--   * 한 다이어리가 여러 페이지를 가질 수 있고, 같은 날짜에 여러 페이지를 만들 수 있다.
--   * 한 페이지가 여러 TEXT/PHOTO/STICKER 요소를 가질 수 있으며, PHOTO 한 장은 diary_elements 한 행이다.
--   * STICKER 는 PHOTO 와 같은 자유배치 이미지 요소다. image_url 과
--     position/size/rotation/z_index 컬럼을 PHOTO 와 똑같이 사용한다. (text_content 는 쓰지 않는다)
--   * 각 페이지는 paper_color(#RRGGBB)로 독립적인 종이 바탕색을 가질 수 있고,
--     background_type(PLAIN/LINED/GRID/DOT) 무늬와 조합해서 쓴다.
--     NULL 이면 기본 종이색을 쓰며, 펼침의 좌/우 페이지 색을 같게 맞추지 않는다.
--   * 페이지 본문은 diary_pages.content 를, 페이지 상단(page_date 오른쪽)의 짧은 한 줄 메모는
--     diary_pages.page_header 를 사용한다. (예: '제주 여행 첫째 날', 'Day 1 ✈️')
--   * 그 한 줄 메모의 꾸밈은 page_header_font(글꼴)와 page_header_bold(0 보통 / 1 굵게)로 함께 저장한다.
--   * 별도 diary_images 테이블은 없고, 대표 이미지만 diaries.cover_image_url 을 사용한다.
--   * 요소의 위치/크기는 페이지 크기 기준 0~1 상대값으로 저장한다.
--

--
-- Table structure for table `diaries`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `diaries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(150) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `cover_image_url` varchar(255) DEFAULT NULL,
  `cover_style` varchar(30) NOT NULL DEFAULT 'DEFAULT',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_diaries_user` (`user_id`,`start_date`,`id`),
  CONSTRAINT `fk_diaries_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_diaries_period` CHECK ((`end_date` >= `start_date`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `diary_elements`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `diary_elements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `page_id` bigint NOT NULL,
  `element_type` varchar(10) NOT NULL,
  `text_content` text,
  `image_url` varchar(255) DEFAULT NULL,
  `position_x` decimal(6,5) NOT NULL DEFAULT '0.00000',
  `position_y` decimal(6,5) NOT NULL DEFAULT '0.00000',
  `width` decimal(6,5) NOT NULL DEFAULT '0.30000',
  `height` decimal(6,5) NOT NULL DEFAULT '0.30000',
  `rotation` decimal(6,2) NOT NULL DEFAULT '0.00',
  `z_index` int NOT NULL DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_diary_elements_page` (`page_id`,`z_index`,`id`),
  CONSTRAINT `fk_diary_elements_page` FOREIGN KEY (`page_id`) REFERENCES `diary_pages` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_diary_elements_type` CHECK ((`element_type` in (_utf8mb4'TEXT',_utf8mb4'PHOTO',_utf8mb4'STICKER'))),
  CONSTRAINT `chk_diary_elements_payload` CHECK ((((`element_type` = _utf8mb4'TEXT') and (`text_content` is not null) and (`image_url` is null)) or ((`element_type` in (_utf8mb4'PHOTO',_utf8mb4'STICKER')) and (`image_url` is not null) and (`text_content` is null)))),
  CONSTRAINT `chk_diary_elements_position` CHECK (((`position_x` between -(0.5) and 1.5) and (`position_y` between -(0.5) and 1.5))),
  CONSTRAINT `chk_diary_elements_size` CHECK (((`width` > 0) and (`width` <= 1) and (`height` > 0) and (`height` <= 1))),
  CONSTRAINT `chk_diary_elements_rotation` CHECK ((`rotation` between -(360) and 360)),
  CONSTRAINT `chk_diary_elements_z_index` CHECK ((`z_index` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `diary_pages`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `diary_pages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `diary_id` bigint NOT NULL,
  `page_date` date NOT NULL,
  `page_order` int NOT NULL,
  `background_type` varchar(30) NOT NULL DEFAULT 'PLAIN',
  `paper_color` varchar(7) DEFAULT NULL,
  `page_header` varchar(100) DEFAULT NULL,
  `page_header_font` varchar(50) NOT NULL DEFAULT 'DEFAULT',
  `page_header_bold` tinyint(1) NOT NULL DEFAULT '0',
  `content` mediumtext COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_diary_pages_order` (`diary_id`,`page_order`),
  KEY `idx_diary_pages_date` (`diary_id`,`page_date`,`page_order`),
  CONSTRAINT `fk_diary_pages_diary` FOREIGN KEY (`diary_id`) REFERENCES `diaries` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_diary_pages_order` CHECK ((`page_order` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `events`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `description` text NOT NULL,
  `event_img` varchar(255) DEFAULT NULL,
  `poster_img` varchar(255) DEFAULT NULL,
  `event_type` varchar(20) NOT NULL DEFAULT 'INFOGRAPHIC',
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `is_slide` tinyint NOT NULL DEFAULT '0',
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_event_users1_idx` (`user_id`),
  CONSTRAINT `fk_events_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `faq_categories`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `faq_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `category_name_UNIQUE` (`category_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `faqs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `faqs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question` varchar(255) NOT NULL,
  `answer` text NOT NULL,
  `order_index` bigint NOT NULL DEFAULT '1',
  `is_visible` tinyint NOT NULL DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `category_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_faqs_faq_categoryies1_idx` (`category_id`),
  KEY `fk_faqs_users1_idx` (`user_id`),
  CONSTRAINT `fk_faqs_faqcategory` FOREIGN KEY (`category_id`) REFERENCES `faq_categories` (`id`),
  CONSTRAINT `fk_faqs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `info_periods`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `info_periods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `info_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_festical_periods_travle_info1_idx` (`info_id`),
  CONSTRAINT `fk_festivalperiods_info` FOREIGN KEY (`info_id`) REFERENCES `travel_info` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `info_categories`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `info_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `display_order` int NOT NULL DEFAULT '1',
  `is_visible` tinyint NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_info_categories_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `info_images`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `info_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `image_url` varchar(255) NOT NULL,
  `is_main` tinyint NOT NULL DEFAULT '0',
  `order_index` int NOT NULL DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `info_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_info_image_travle_info1_idx` (`info_id`),
  CONSTRAINT `fk_infoimages_info` FOREIGN KEY (`info_id`) REFERENCES `travel_info` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inquiries`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inquiries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subject` varchar(255) NOT NULL,
  `content` text NOT NULL,
  `status` varchar(50) NOT NULL DEFAULT 'PENDING',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `user_id` bigint NOT NULL,
  `inquiry_type` varchar(30) NOT NULL DEFAULT 'OTHER',
  PRIMARY KEY (`id`),
  KEY `fk_inquiry_users1_idx` (`user_id`),
  KEY `idx_inquiries_user_created` (`user_id`,`created_at`,`id`),
  KEY `idx_inquiries_status_created` (`status`,`created_at`,`id`),
  CONSTRAINT `fk_inquiries_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inquiry_answers`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inquiry_answers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `inquiry_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_inquiry_answers_inquiry_id` (`inquiry_id`),
  KEY `fk_inquiry_answer_inquiry1_idx` (`inquiry_id`),
  KEY `fk_inquiry_answer_users1_idx` (`user_id`),
  CONSTRAINT `fk_inquiryanswers_inquiry` FOREIGN KEY (`inquiry_id`) REFERENCES `inquiries` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_inquiryanswers_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notices`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `content` mediumtext NOT NULL,
  `is_pinned` tinyint NOT NULL DEFAULT '0',
  `views` int NOT NULL DEFAULT '0',
  `user_id` bigint NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notices_public_order` (`is_pinned`,`created_at`,`id`),
  KEY `idx_notices_user_id` (`user_id`),
  CONSTRAINT `fk_notices_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `post_comment_images`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_comment_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_id` bigint NOT NULL,
  `image_url` varchar(255) NOT NULL,
  `display_order` int NOT NULL DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_post_comment_images_order` (`comment_id`,`display_order`),
  CONSTRAINT `fk_post_comment_images_comment` FOREIGN KEY (`comment_id`) REFERENCES `post_comments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_post_comment_images_order` CHECK (((`display_order` >= 1) and (`display_order` <= 3)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `post_comment_likes`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_comment_likes` (
  `user_id` bigint NOT NULL,
  `comment_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`comment_id`),
  KEY `fk_post_comment_likes_comment` (`comment_id`),
  CONSTRAINT `fk_post_comment_likes_comment` FOREIGN KEY (`comment_id`) REFERENCES `post_comments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_post_comment_likes_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `post_comments`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `parent_comment_id` bigint DEFAULT NULL,
  `reply_to_comment_id` bigint DEFAULT NULL,
  `likes` int NOT NULL DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` timestamp NULL DEFAULT NULL,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `fk_post_comment_user_post1_idx` (`post_id`),
  KEY `fk_post_comment_users1_idx` (`user_id`),
  KEY `fk_comment_parent_idx` (`parent_comment_id`),
  KEY `idx_post_comments_reply_to` (`reply_to_comment_id`),
  CONSTRAINT `fk_postcomments_post` FOREIGN KEY (`post_id`) REFERENCES `user_posts` (`id`),
  CONSTRAINT `fk_postcomments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `post_images`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `image_url` varchar(255) NOT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `uploaded_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL,
  `post_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_post_image_user_post1_idx` (`post_id`),
  CONSTRAINT `fk_postimages_post` FOREIGN KEY (`post_id`) REFERENCES `user_posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `region_tag_map`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `region_tag_map` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_regiontagmap_category` (`category_id`),
  KEY `fk_regiontagmap_tag` (`tag_id`),
  CONSTRAINT `fk_regiontagmap_category` FOREIGN KEY (`category_id`) REFERENCES `country_categories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_regiontagmap_tag` FOREIGN KEY (`tag_id`) REFERENCES `region_tags` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `region_tags`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `region_tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name_ko` varchar(50) NOT NULL,
  `name_en` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `restaurant_amenities`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `restaurant_amenities` (
  `restaurant_id` bigint NOT NULL,
  `amenity_id` int NOT NULL,
  PRIMARY KEY (`restaurant_id`,`amenity_id`),
  KEY `amenity_id` (`amenity_id`),
  CONSTRAINT `restaurant_amenities_ibfk_1` FOREIGN KEY (`restaurant_id`) REFERENCES `restaurant_info` (`destination_id`) ON DELETE CASCADE,
  CONSTRAINT `restaurant_amenities_ibfk_2` FOREIGN KEY (`amenity_id`) REFERENCES `amenities` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `restaurant_info`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `restaurant_info` (
  `destination_id` bigint NOT NULL,
  `main_menu` varchar(64) DEFAULT NULL,
  `price_range` varchar(32) DEFAULT NULL,
  `opening_hours` varchar(64) DEFAULT NULL,
  `break_time` varchar(32) DEFAULT NULL,
  `closed_days` varchar(32) DEFAULT NULL,
  `parking_available` tinyint(1) DEFAULT NULL,
  `pet_allowed` tinyint(1) DEFAULT NULL,
  `seat_count` int DEFAULT NULL,
  `takeout_available` tinyint(1) DEFAULT NULL,
  `delivery_available` tinyint(1) DEFAULT NULL,
  `reservation` tinyint(1) DEFAULT NULL,
  `contact_number` varchar(32) DEFAULT NULL,
  `homepage_url` varchar(255) DEFAULT NULL,
  `etc` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`destination_id`),
  CONSTRAINT `fk_restaurant_dest_id` FOREIGN KEY (`destination_id`) REFERENCES `destinations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `shop_amenities`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shop_amenities` (
  `shop_id` bigint NOT NULL,
  `amenity_id` int NOT NULL,
  PRIMARY KEY (`shop_id`,`amenity_id`),
  KEY `amenity_id` (`amenity_id`),
  CONSTRAINT `shop_amenities_ibfk_1` FOREIGN KEY (`shop_id`) REFERENCES `shop_info` (`destination_id`) ON DELETE CASCADE,
  CONSTRAINT `shop_amenities_ibfk_2` FOREIGN KEY (`amenity_id`) REFERENCES `amenities` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `shop_info`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shop_info` (
  `destination_id` bigint NOT NULL,
  `closed_days` varchar(500) DEFAULT NULL,
  `opening_hours` varchar(1000) DEFAULT NULL,
  `main_products` varchar(255) DEFAULT NULL,
  `parking_available` tinyint(1) DEFAULT NULL,
  `contact_number` varchar(255) DEFAULT NULL,
  `homepage_url` varchar(255) DEFAULT NULL,
  `guide` text DEFAULT NULL,
  PRIMARY KEY (`destination_id`),
  CONSTRAINT `fk_shop_dest_id` FOREIGN KEY (`destination_id`) REFERENCES `destinations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `travel_info`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `travel_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `content` mediumtext NOT NULL,
  `scope` varchar(20) NOT NULL,
  `content_type` varchar(20) NOT NULL DEFAULT 'GENERAL',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `category_id` bigint NOT NULL,
  `views` int NOT NULL DEFAULT '0',
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_travle_info_info_category1_idx` (`category_id`),
  KEY `fk_travle_info_users1_idx` (`user_id`),
  CONSTRAINT `fk_travelinfo_category` FOREIGN KEY (`category_id`) REFERENCES `info_categories` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_travelinfo_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_account_actions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_account_actions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '조치 대상 회원',
  `action_type` varchar(30) NOT NULL COMMENT 'FORCED_WITHDRAWAL',
  `sanction_id` bigint DEFAULT NULL COMMENT '이어진 제재가 있으면 연결(없으면 NULL)',
  `reason` varchar(500) NOT NULL COMMENT '조치 사유',
  `admin_note` text COMMENT '내부 메모(비공개)',
  `created_by` bigint NOT NULL COMMENT '조치 관리자',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_account_actions_user_created` (`user_id`,`created_at`,`id`),
  KEY `idx_user_account_actions_type_created` (`action_type`,`created_at`,`id`),
  KEY `idx_user_account_actions_admin_created` (`created_by`,`created_at`),
  KEY `idx_user_account_actions_sanction` (`sanction_id`),
  CONSTRAINT `fk_user_account_actions_admin` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_user_account_actions_sanction` FOREIGN KEY (`sanction_id`) REFERENCES `user_sanctions` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_account_actions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_appeals`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_appeals` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sanction_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT(이메일 인증 대기) | PENDING | APPROVED | REJECTED',
  `content` text COMMENT '제출 시 채워짐',
  `token_hash` char(64) CHARACTER SET ascii COLLATE ascii_general_ci DEFAULT NULL,
  `token_exp` datetime DEFAULT NULL,
  `requested_at` datetime DEFAULT NULL COMMENT '인증메일 발송 시각(쿨다운)',
  `verified_at` datetime DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `admin_id` bigint DEFAULT NULL,
  `admin_reply` varchar(1000) DEFAULT NULL COMMENT '승인/기각 사유',
  `handled_at` datetime DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `pending_sanction_id` bigint GENERATED ALWAYS AS ((case when (`status` = 'PENDING') then `sanction_id` end)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_appeals_pending` (`pending_sanction_id`),
  UNIQUE KEY `uq_user_appeals_token` (`token_hash`),
  KEY `idx_user_appeals_status_created` (`status`,`created_at`,`id`),
  KEY `idx_user_appeals_sanction` (`sanction_id`),
  KEY `idx_user_appeals_user_created` (`user_id`,`created_at`),
  KEY `fk_user_appeals_admin` (`admin_id`),
  CONSTRAINT `fk_user_appeals_admin` FOREIGN KEY (`admin_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_user_appeals_sanction` FOREIGN KEY (`sanction_id`) REFERENCES `user_sanctions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_appeals_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_posts`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_posts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `content` mediumtext,
  `post_type` varchar(20) NOT NULL,
  `views` int NOT NULL DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` timestamp NULL DEFAULT NULL,
  `deleted` tinyint DEFAULT '0',
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_user_post_users1_idx` (`user_id`),
  CONSTRAINT `fk_userposts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_sanctions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_sanctions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` varchar(20) NOT NULL COMMENT 'TEMPORARY | PERMANENT',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | EXPIRED | LIFTED',
  `reason` varchar(500) NOT NULL COMMENT '회원 안내용 사유',
  `admin_note` text COMMENT '내부 메모(비공개)',
  `previous_status` varchar(20) NOT NULL COMMENT '제재 직전 users.status (해제 시 복원)',
  `starts_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime DEFAULT NULL COMMENT 'TEMPORARY만 값 존재, PERMANENT는 NULL',
  `released_at` datetime DEFAULT NULL,
  `released_by` bigint DEFAULT NULL,
  `released_via` varchar(20) DEFAULT NULL COMMENT 'ADMIN | APPEAL | SYSTEM',
  `release_reason` varchar(500) DEFAULT NULL,
  `created_by` bigint NOT NULL COMMENT '조치 관리자',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `active_user_id` bigint GENERATED ALWAYS AS ((case when (`status` = 'ACTIVE') then `user_id` end)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_sanctions_active` (`active_user_id`),
  KEY `idx_user_sanctions_user_created` (`user_id`,`created_at`,`id`),
  KEY `idx_user_sanctions_expiry` (`status`,`expires_at`),
  KEY `idx_user_sanctions_status_created` (`status`,`created_at`,`id`),
  KEY `fk_user_sanctions_admin` (`created_by`),
  KEY `fk_user_sanctions_release` (`released_by`),
  CONSTRAINT `fk_user_sanctions_admin` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_user_sanctions_release` FOREIGN KEY (`released_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_user_sanctions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `full_name` varchar(50) NOT NULL,
  `user_phone` varchar(20) DEFAULT NULL,
  `user_birth` date NOT NULL,
  `user_email` varchar(100) NOT NULL,
  `username` varchar(50) NOT NULL,
  `user_password` varchar(255) NOT NULL,
  `nickname` varchar(50) NOT NULL,
  `user_role` varchar(50) NOT NULL,
  `verification_token` varchar(255) DEFAULT NULL,
  `verification_token_exp` datetime DEFAULT NULL,
  `verification_requested_at` datetime DEFAULT NULL,
  `profile_image` varchar(255) DEFAULT NULL,
  `status` enum('INACTIVE','ACTIVE','SUSPENDED','DEACTIVATED','RESTRICTED') NOT NULL DEFAULT 'INACTIVE',
  `last_login` timestamp NULL DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `reset_token` varchar(255) DEFAULT NULL,
  `reset_token_exp` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_email_UNIQUE` (`user_email`),
  UNIQUE KEY `username_UNIQUE` (`username`),
  UNIQUE KEY `nickname_UNIQUE` (`nickname`),
  UNIQUE KEY `verification_token_UNIQUE` (`verification_token`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-06  3:33:22
