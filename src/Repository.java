import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;
public class Repository {
    private final File CWD;
    private final File Gitlet_Dir;
    
    private final File Branches_Dir;
    private final File Blobs_Dir;
    private final File Commits_Dir;
    private final File Staged_Dir;
    private final File Addition_Dir;
    private final File Removal_Dir;
    private final File Head_file;

    private final Head head;
    private final CommitStore commitStore;
    private final BranchStore branchStore;
    private final BlobStore blobStore;
    private final WorkingArea workingArea;
    private final StagingArea stagingArea;
    public Repository(String cwd)
    {
           CWD=new File(cwd);
            
            if(!CWD.exists()) throw new RuntimeException("Working directory not found.");
            
            Gitlet_Dir=Utils.join(CWD,".gitlet");
            Branches_Dir=Utils.join(Gitlet_Dir,"branches");
            Blobs_Dir=Utils.join(Gitlet_Dir,"blobs");
            Commits_Dir=Utils.join(Gitlet_Dir, "commits");
            Staged_Dir=Utils.join(Gitlet_Dir, "staged");
            Addition_Dir=Utils.join(Staged_Dir, "addition");
            Removal_Dir=Utils.join(Staged_Dir, "removal");
            Head_file=Utils.join(Gitlet_Dir,"head");
             
            commitStore = new CommitStore(Commits_Dir);
            branchStore=new BranchStore(Branches_Dir);
            blobStore=new BlobStore(Blobs_Dir);
            workingArea=new WorkingArea(CWD);
            stagingArea=new StagingArea(Addition_Dir,Removal_Dir);
            head=new Head(Head_file);
    }
   
    public void init() {
        if (Gitlet_Dir.exists()) Utils.exitWithMessage("Gitlet Repository already exists in current working directory");

        Gitlet_Dir.mkdir();
        Blobs_Dir.mkdir();
        Commits_Dir.mkdir();
        Staged_Dir.mkdir();
        Addition_Dir.mkdir();
        Removal_Dir.mkdir();
        Branches_Dir.mkdir();

        try {
            Head_file.createNewFile();
        } catch (java.io.IOException ex) {
            Utils.exitWithMessage(ex.getMessage());
        }
        Commit commit = new Commit(new Date(0), "Initial Commit");   //Initialize the directory structure inside .gitlet
        // start with initial commit with message "initial commit" and timestamp=Unix epoch
        commitStore.saveCommit(commit);
        Branch master = new Branch("master", commit.getCommitHash());// create master branch
        branchStore.saveBranch(master);
        head.setHead(master); //set head to point to master
    }
  
///If the file in the working directory is different from the file in the staging area:
///Git will create a new blob object for the updated content.
//The staging area is updated to reference this new blob, meaning the new content will be included in the next commit.
//If the file in the working directory is the same as the file in the staging area:
//No new blob is created, and the file is not updated in the staging area. The staging area already has the correct content, so there is no need to re-stage the file.
    public void add(String fileName) {
        //check gitlet repo existence
        checkGitletExistense();
        //check file existense
        File currentFile = workingArea.checkFileExistense(fileName);
        if (currentFile == null) Utils.exitWithMessage("File doesn't exist");


        String curFileHash = Utils.sha1(Utils.readContentsAsString(currentFile));

        if (!stagingArea.checkBlobExistense(fileName, curFileHash)) {
            //there is modification and last version must be added to staging area
            //so create a new blob
            String fileHashCWD = blobStore.saveBlob(currentFile);
            //then refer to that blob at staging area (adding new version to staging area)
            stagingArea.stageForAddition(fileName, fileHashCWD);
        } else System.out.println("File '" + fileName + "' is already up-to-date in the staging area.");


        stagingArea.unstageForRemoval(fileName);
    }

    public void rm(String fileName)
    {
    //check gitlet repo existence
    checkGitletExistense();
        
      boolean checkFileStagedForAddition=stagingArea.CheckFileStagedForAddition(fileName);
      
   
        //check if file tracked or not
        String fileHash=getCurrentCommit().trackedFiles().get(fileName);
        if(fileHash==null) //untracked
        {
          if(checkFileStagedForAddition) stagingArea.UnStageForAddittion(fileName);
          else System.out.println("No reason to remove the file."); //untracked and not staged for addition
        }
        else
        {
          if(checkFileStagedForAddition) stagingArea.UnStageForAddittion(fileName);
            ///add last commited file version to staged for removal 
            stagingArea.StageForRemoval(fileName, fileHash);
            ///remove current file version form CWD
             workingArea.removeFromCWD(fileName);
        }

    }

     public void log()
     {
         //check gitlet repo existence
    checkGitletExistense();
    //get current commit
    Commit curCommit=getCurrentCommit();
     ArrayList<Commit>listOfCommits= branchStore.getBranchHistory(curCommit,commitStore);
      for(int i=0;i<listOfCommits.size();i++)
      {
        System.out.println("===");
        System.out.println(listOfCommits.get(i));
      }
     
    }



    private void checkGitletExistense() {
      if (!Gitlet_Dir.exists()) {
          Utils.exitWithMessage("initialized Gitlet directory doesn't exist.");
      }
  }

  public void commit(String Message) {
      checkGitletExistense(); // check repo is initialized
      commit(Message , null);
  }


  public void commit(String Message, String SecondParentHash) {
      
    if (Message.isEmpty()) Utils.exitWithMessage("you have to enter a commit message");
      if (stagingArea.IsEmpty()) Utils.exitWithMessage("nothing to commit");
      
      
      String CurCommitHash = getCurrentCommit().getCommitHash();
      
      Map<String , String> trackedFiles = commitStore.getCommit(CurCommitHash).trackedFiles();
      
      for(File f : stagingArea.GetFilesForAddition()){
          String BlobStored = Utils.readContentsAsString(f);
          trackedFiles.put(f.getName() , BlobStored);
      }
      for(File f: stagingArea.GetFilesForRemoval()){
          trackedFiles.remove(f.getName());
      }
      
      Commit NewCommit = new Commit(new Date(), Message, SecondParentHash,CurCommitHash, trackedFiles);
      commitStore.saveCommit(NewCommit);
      
      Branch CurBranch=getCurrentBranch();
      CurBranch.SetCommit(NewCommit.getCommitHash());
      branchStore.saveBranch(CurBranch);
      stagingArea.clear();
  }
  //get active branch
  private Branch getCurrentBranch() 
  { 
    return branchStore.getBranch(head.getHead());
  }
//get current commit refered to by active branch
private Commit getCurrentCommit()
{
  String curCommitHash=getCurrentBranch().getReferredCommitHash();
return commitStore.getCommit(curCommitHash);
}


}
