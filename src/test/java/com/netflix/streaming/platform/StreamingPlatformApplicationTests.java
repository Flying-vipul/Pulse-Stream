package com.netflix.streaming.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "AZURE_STORAGE_CONNECTION_STRING=DefaultEndpointsProtocol=https;AccountName=dummy;AccountKey=dummy;EndpointSuffix=core.windows.net",
    "RAZORPAY_KEY_ID=dummy",
    "RAZORPAY_KEY_SECRET=dummy",
    "OAUTH_CLIENT_ID=dummy",
    "OAUTH_CLIENT_SECRET=dummy",
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:testdb",
    "SPRING_DATASOURCE_USERNAME=sa",
    "SPRING_DATASOURCE_PASSWORD=",
    "RESEND_API_KEY=dummy",
    "JWT_SECRET=mySecretKeyThatIsVeryLongAndSecureForTesting1234567890",
    "CLOUDINARY_NAME=dummy",
    "CLOUDINARY_API_KEY=dummy",
    "CLOUDINARY_API_SECRET=dummy",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
class StreamingPlatformApplicationTests {

	@Test
	void contextLoads() {
	}

}
