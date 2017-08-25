Instructions
-----------

Prerequisites:
* If running on Windows, you must install the http://kdiff3.sourceforge.net/ tool on the root of C: (after installed, the path "C:/KDiff3/bin/diff3.exe" must exist)
* https://www.r-project.org/ must be installed

After cloning this repository:
1. Update the file "config.properties" accordingly with the path where the experiment will be executed
2. Update the file "projects.csv" if desired, each row contains the name of a project and its github url
3. Execute the command `java -jar experiment.jar`. Beware a single run can take several days.
4. Run the script statistics.R (you must update lines 8 and 11 of the script)

Contact: delaevernu@gmail.com
