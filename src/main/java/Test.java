public class Test {
    float rotationPitch;
    float carPitch;

    protected void test(float d0) throws Throwable {
        if (d0 < 10.0) {
            System.out.println("g");
        } else {
            System.out.println("l");
        }
    }

    protected static void test1(Test t, float d0) throws Throwable {
        t.rotationPitch = (float) ((double) t.rotationPitch + ((double) t.carPitch - (double) t.rotationPitch) * (double) d0);
    }

}
