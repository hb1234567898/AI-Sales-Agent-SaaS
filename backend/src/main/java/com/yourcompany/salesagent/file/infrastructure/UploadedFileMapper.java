package com.yourcompany.salesagent.file.infrastructure;

import java.util.List;
import java.util.UUID;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yourcompany.salesagent.file.domain.UploadedFile;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UploadedFileMapper extends BaseMapper<UploadedFile> {

	@Select("""
			SELECT id, organization_id, uploaded_by_member_id, customer_id, original_filename,
			       content_type, size_bytes, sha256, content, status, created_at, updated_at, version
			FROM uploaded_file
			WHERE organization_id = #{organizationId}
			  AND customer_id = #{customerId}
			  AND status = 'ACTIVE'
			ORDER BY created_at DESC, id DESC
			LIMIT #{limit}
			""")
	List<UploadedFile> selectRecentForCustomer(
			@Param("organizationId") UUID organizationId,
			@Param("customerId") UUID customerId,
			@Param("limit") int limit);

	@Select("""
			SELECT id, organization_id, uploaded_by_member_id, customer_id, original_filename,
			       content_type, size_bytes, sha256, content, status, created_at, updated_at, version
			FROM uploaded_file
			WHERE organization_id = #{organizationId}
			  AND id = #{fileId}
			  AND status = 'ACTIVE'
			LIMIT 1
			""")
	UploadedFile selectActive(
			@Param("organizationId") UUID organizationId,
			@Param("fileId") UUID fileId);
}
