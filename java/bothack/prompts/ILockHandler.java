package bothack.prompts;

public interface ILockHandler {
	Boolean lockIt(String prompt);
	Boolean unlockIt(String prompt);
}
