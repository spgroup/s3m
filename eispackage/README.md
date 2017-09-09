Instructions
-----------

Prerequisites:
* If running on Windows, you must install the http://kdiff3.sourceforge.net/ tool on the root of C: (after installed, the path "C:/KDiff3/bin/diff3.exe" must exist)
* https://www.r-project.org/ must be installed, with the following packages (just copy and paste on R console):

	`install.packages('ggplot2', repos='http://cran.us.r-project.org');
	install.packages('reshape2', repos='http://cran.us.r-project.org');
	install.packages('beanplot', repos='http://cran.us.r-project.org');
	install.packages('xlsx', repos='http://cran.us.r-project.org');
	install.packages('coin', repos='http://cran.us.r-project.org');
	install.packages('R2HTML', repos='http://cran.us.r-project.org');
	install.packages('rJava', repos='http://cran.us.r-project.org')`

After cloning this repository:
1. Update the file "config.properties" accordingly with the path where the experiment will be executed
2. Update the file "projects.csv" if desired, each row contains the name of a project and its github url
3. Execute the command `java -jar experiment.jar`. Beware a single run can take several days
4. Run the script "statistics.R" (you must update lines 8 and 11 of the script)

A HTML page will be created on the *results/html* folder summarizing the results. Besides, a number of sheets and logs will be created on the *results/* folder; for instance, the log of renaming conflicts is *log_ssmerge_renaming.csv*.

The entire source code of this replication package is available on: https://github.com/guilhermejccavalcanti/sourceeis 

Contact: gjcc@cin.ufpe.br
