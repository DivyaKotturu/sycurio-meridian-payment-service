package com.example.payments;
import com.example.payments.api.dto.*;
import com.example.payments.domain.PaymentStatus;
import com.example.payments.service.WebhookSignature;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties={"spring.datasource.url=jdbc:h2:mem:sycuriotest;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop"})
class PaymentIntegrationTest {
 @Autowired TestRestTemplate http;
 private HttpEntity<CreatePaymentRequest> request(String key,BigDecimal amount){HttpHeaders h=new HttpHeaders();h.set("Idempotency-Key",key);return new HttpEntity<>(new CreatePaymentRequest("tok_test",amount,"GBP"),h);}
 @Test void createIdempotencyAndWebhook() {
  String key=UUID.randomUUID().toString();String url="/api/v1/payments";
  var first=http.exchange(url,HttpMethod.POST,request(key,new BigDecimal("125.50")),PaymentResponse.class);
  assertEquals(HttpStatus.ACCEPTED,first.getStatusCode());assertEquals(PaymentStatus.PDNG,first.getBody().status());
  var duplicate=http.exchange(url,HttpMethod.POST,request(key,new BigDecimal("125.50")),PaymentResponse.class);
  assertEquals(first.getBody().id(),duplicate.getBody().id());
  var conflict=http.exchange(url,HttpMethod.POST,request(key,new BigDecimal("99.00")),String.class);
  assertEquals(HttpStatus.CONFLICT,conflict.getStatusCode());
  String provider=first.getBody().providerPaymentId();
  var completed=http.postForEntity("/mock-meridian/v1/payments/"+provider+"/simulate?status=ACCP",null,MeridianPaymentResponse.class);
  assertEquals(PaymentStatus.ACCP,completed.getBody().status());
  var status=http.getForEntity("/api/v1/payments/"+first.getBody().id(),PaymentResponse.class);
  assertEquals(PaymentStatus.ACCP,status.getBody().status());
  var cancelled=http.postForEntity("/api/v1/payments/"+first.getBody().id()+"/cancel",null,PaymentResponse.class);
  assertEquals(PaymentStatus.CANC,cancelled.getBody().status());
 }
 @Test void rejectUnsignedWebhook(){var response=http.postForEntity("/api/v1/webhooks/meridian",new MeridianWebhookRequest("evt","mer","ref",PaymentStatus.ACCP,null,null),String.class);assertEquals(HttpStatus.UNAUTHORIZED,response.getStatusCode());}

 @Test void settlementCannotBeCancelled() {
  String key=UUID.randomUUID().toString();
  var first=http.exchange("/api/v1/payments",HttpMethod.POST,request(key,new BigDecimal("45.00")),PaymentResponse.class);
  String provider=first.getBody().providerPaymentId();
  assertEquals(HttpStatus.OK,http.postForEntity("/mock-meridian/v1/payments/"+provider+"/simulate?status=ACCP",null,MeridianPaymentResponse.class).getStatusCode());
  assertEquals(HttpStatus.OK,http.postForEntity("/mock-meridian/v1/payments/"+provider+"/simulate?status=ACSC",null,MeridianPaymentResponse.class).getStatusCode());
  var cancelled=http.postForEntity("/api/v1/payments/"+first.getBody().id()+"/cancel",null,String.class);
  assertEquals(HttpStatus.CONFLICT,cancelled.getStatusCode());
 }
 @Test void invalidAmountRejected() {
  var response=http.exchange("/api/v1/payments",HttpMethod.POST,request(UUID.randomUUID().toString(),new BigDecimal("-1.00")),String.class);
  assertEquals(HttpStatus.BAD_REQUEST,response.getStatusCode());
 }
}
