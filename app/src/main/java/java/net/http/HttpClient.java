package java.net.http;
// stupid hack to stop a library crapping out
public class HttpClient {
    public static Builder newBuilder(){
        return new HttpBuilder();
    }
    public static class HttpBuilder implements Builder{

        @Override
        public Builder followRedirects(Redirect redirect) {
            return this;
        }

        @Override
        public HttpClient build(){
            return null;
        }
    }
    public interface Builder{
        Builder followRedirects(Redirect redirect);
        HttpClient build();
    }
    public static class Redirect {
        public static Redirect NEVER;
    }
}
