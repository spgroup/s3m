Instructions
-----------

Pre-requisites:
* https://git-scm.com/downloads   must be installed 
* If running on Windows, you must install the http://kdiff3.sourceforge.net/ tool on the root of C: (after installed, the path "C:/KDiff3/bin/diff3.exe" must exist)
* https://www.r-project.org/ must be installed, with the following packages (just copy and paste on R console):

	`install.packages('ggplot2', repos='http://cran.us.r-project.org');
	install.packages('reshape2', repos='http://cran.us.r-project.org');
	install.packages('beanplot', repos='http://cran.us.r-project.org');
	install.packages('xlsx', repos='http://cran.us.r-project.org');
	install.packages('coin', repos='http://cran.us.r-project.org');
	install.packages('scales', repos='http://cran.us.r-project.org'); 
	install.packages('knitr', repos='http://cran.us.r-project.org');
	install.packages('kableExtra', repos='http://cran.us.r-project.org');
	install.packages('DT', repos='http://cran.us.r-project.org');
	install.packages('tidyr', repos='http://cran.us.r-project.org');
	install.packages('effsize', repos='http://cran.us.r-project.org');
	install.packages('dplyr', repos='http://cran.us.r-project.org');`
* https://www.rstudio.com must be installed


After cloning this repository:
1. Install semistructured and structured merge with the command `java -jar mergetool-installer.jar`
2. Update the file "config.properties" accordingly with the path where the experiment will be executed
3. Update the file "projects.csv" if desired, each row contains the name of a project and its github url
4. Execute the command `java -jar experiment.jar`. Beware a single run can take several days
5. Open the script "statistics.Rmd" (you must update line 54 of this script) on RStudio installed on the prerequisites 
6. Run the script with `ctrl + shift + k`
6. Uninstall semistructured and structured merge with the uninstaller provided on the instalation folder you gave on step 1.

A HTML page "statistics.html" will be created summarizing the results. 



Dataset
-----------

Our dataset is packed in the file `dataset.zip`. It consists of the results of semistructured and structured merge applied to each file, merge scenario and project of our sample. Also, it presents the Travis CI status for the cleanly merged scenarios, and the results of our manual inspections.

In particular, the dataset contains the following files:

`numbers-projects.csv`
> a number of metrics aggreggated per-project of our sample, such as number of merge scenarios, number of conflicts of each merge strategy, number of successfull builds on Travis CI per merge strategy, and so on.

`numbers-scenarios-builds-semistructured.csv` 
> aggreggated metrics for each merge scenario in of our sample, including the Travis CI status for scenarios that resulted in merge conflicts with structured merge, but not with semistructured merge.

`numbers-scenarios-builds-structured.csv` 
> aggreggated metrics for each merge scenario of each project in our sample, including the Travis CI status for scenarios that resulted in merge conflicts with semistructured merge, but not with structured merge.

`numbers-files.csv` 
> metrics for each merged file of each project in our sample

`manual-analysis.csv` 
> findings about the manual inspection of the merge scenarios with a passed Travis CI status, indicating whether the scenario has false positives of the strategy that reported merge conflicts, or false negatives of the strategy that cleanly merged the scenario.