package com.zvonok.service;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.zvonok.exception.LiveKitRoomSyncException;
import io.livekit.server.EgressServiceClient;
import livekit.LivekitEgress;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Service
@Slf4j
public class LiveKitEgressAdminService {

	private final EgressServiceClient egressClient;

	@Value("${s3.endpoint}")
	private String s3Endpoint;

	@Value("${s3.access-key}")
	private String s3AccessKey;

	@Value("${s3.secret-access-key}")
	private String s3SecretKey;

	@Value("${s3.region}")
	private String s3Region;

	@Value("${recording.s3.force-path-style:true}")
	private boolean forcePathStyle;

	public LiveKitEgressAdminService(
		@Value("${livekit.api-url}") String livekitApiUrl,
		@Value("${livekit.api-key}") String apiKey,
		@Value("${livekit.api-secret}") String apiSecret
	) {
		this.egressClient = EgressServiceClient.createClient(livekitApiUrl, apiKey, apiSecret);
	}

	public LivekitEgress.EgressInfo startRoomCompositeMp4(String roomName, String filePath) {
		try {
			LivekitEgress.EncodedFileOutput fileOutput = 
				LivekitEgress.EncodedFileOutput.newBuilder()
					.setFileType(LivekitEgress.EncodedFileType.MP4)	
					.setFilepath(filePath)
					.setS3(LivekitEgress.S3Upload.newBuilder()
							.setEndpoint(s3Endpoint)
							.setAccessKey(s3AccessKey)
							.setSecret(s3SecretKey)
							.setRegion(s3Region)
							.setBucket("egrees")
							.setForcePathStyle(forcePathStyle)
							.build()
							)
					.build();

			Response<LivekitEgress.EgressInfo> response =
				egressClient.startRoomCompositeEgress(
						roomName,
						fileOutput,
						"grid",
						LivekitEgress.EncodingOptionsPreset.H264_1080P_60,
						null,
						false,
						false,
						""
				).execute();

			if (!response.isSuccessful() || response.body() == null) {
				throw new LiveKitRoomSyncException(
					"Failed to start room composite egress. status=" + response.code()
				);
			}

			return response.body();
		} catch (IOException e) {
			log.warn("Failed to start LiveKit egress. room={}, filePath={}", roomName, filePath, e);
			throw new LiveKitRoomSyncException("Failed to start LiveKit egress: " + roomName);
		}
	}

	public LivekitEgress.EgressInfo stopEgress(String egressId) {
		try {
			Response<LivekitEgress.EgressInfo> response = 
				egressClient.stopEgress(egressId).execute();

			if (!response.isSuccessful() || response.body() == null) {
				throw new LiveKitRoomSyncException(
					"Failed to stop egress. status=" + response.code() + ", egressId" + egressId
				);
			}

			return response.body();
		} catch (IOException e) {
			log.warn("Failed to stop LiveKit egress. egressId={}", egressId, e);
			throw new LiveKitRoomSyncException("Failed to stop LiveKit egress " + egressId);
		}
	}
}
