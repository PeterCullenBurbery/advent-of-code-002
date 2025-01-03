#lang racket

(require racket/string
         racket/list
         racket/hash)

;; Step 1: Read file content
(define (read-file file-path)
  (with-input-from-file file-path
    (lambda ()
      (let loop ([lines '()])
        (let ([line (read-line)])
          (if (eof-object? line)
              (apply string-append (reverse lines))
              (loop (cons (string-append line "\n") lines))))))))

;; Step 2: Split content into rules and updates sections
(define (split-sections content)
  (let* ([trimmed (string-trim content)]
         [sections (regexp-split #rx"\n\n+" trimmed)]) ; Handle multiple newlines
    (values (list-ref sections 0)
            (list-ref sections 1))))

;; Step 3: Parse rules section into a list of pairs
(define (parse-rules rules-section)
  (map (lambda (line)
         (let* ([parts (string-split line "|")]
                [x (string->number (string-trim (list-ref parts 0)))]
                [y (string->number (string-trim (list-ref parts 1)))])
           (cons x y)))
       (string-split rules-section "\n")))

;; Step 4: Parse updates section into a list of lists
(define (parse-updates updates-section)
  (map (lambda (line)
         (map string->number (string-split line ",")))
       (string-split updates-section "\n")))

;; Step 5: Check if an update follows the rules
(define (is-update-ordered update rules)
  (let ([index-map
         (for/hash ([page (in-list update)]
                    [idx (in-naturals)])
           page idx)])
    (andmap (lambda (rule)
              (let ([x (car rule)]
                    [y (cdr rule)])
                (and (hash-has-key? index-map x)
                     (hash-has-key? index-map y)
                     (<= (hash-ref index-map x)
                         (hash-ref index-map y)))))
            rules)))

;; Step 6: Extract middle page from an update
(define (get-middle-page update)
  (list-ref update (quotient (length update) 2)))

;; Step 7: Sum elements of a list
(define (sum-list lst)
  (foldl + 0 lst))

;; -------------------------------
;; Manual Queue Implementation
;; -------------------------------

;; A queue is represented as a pair of lists: (front . rear)
(define (make-queue)
  (cons '() '()))

;; Enqueue an element: add to rear
(define (enqueue! queue element)
  (cons (car queue) (cons element (cdr queue))))

;; Dequeue an element: remove from front
;; Returns a pair: (dequeued-element . new-queue)
(define (dequeue! queue)
  (let ([front (car queue)]
        [rear (cdr queue)])
    (cond
      [(not (null? front))
       (values (car front) (cons (cdr front) rear))]
      [(not (null? rear))
       (let ([new-front (reverse rear)])
         (values (car new-front) (cons (cdr new-front) '())))]
      )
      [else
       (error "Queue is empty")])))

;; Check if the queue is empty
(define (queue-empty? queue)
  (and (null? (car queue))
       (null? (cdr queue))))

;; Step 8: Topological sort to correct an update
(define (topological-sort-update update rules)
  (let* ([nodes (remove-duplicates update)]
         [graph (make-hash)]
         [in-degree (make-hash)])
    
    ;; Initialize graph and in-degree
    (for ([rule rules])
      (let ([x (car rule)]
            [y (cdr rule)])
        (when (and (member x nodes)
                   (member y nodes))
          ;; Update graph
          (hash-update! graph x
                        (lambda (existing)
                          (cons y (or existing '())))
                        (list y))
          ;; Update in-degree
          (hash-update! in-degree y
                        (lambda (count) (+ count 1))
                        1)
          ;; Ensure x is in in-degree map
          (hash-update! in-degree x
                        (lambda (count) count)
                        0))))
    
    ;; Initialize queue with nodes having in-degree 0
    (define q (make-queue))
    (for ([node nodes])
      (when (zero? (hash-ref in-degree node 0))
        (set! q (enqueue! q node))))
    
    ;; Perform the topological sort
    (define sorted-update '())
    (let loop ()
      (unless (queue-empty? q)
        (define-values (current new-queue) (dequeue! q))
        (set! q new-queue)
        (set! sorted-update (append sorted-update (list current)))
        (for ([neighbor (hash-ref graph current '())])
          (hash-update! in-degree neighbor
                        (lambda (count) (- count 1))
                        (hash-ref in-degree neighbor 0))
          (when (zero? (hash-ref in-degree neighbor 0))
            (set! q (enqueue! q neighbor))))
        (loop)))
    sorted-update))

;; Step 9: Process updates to identify correctly and incorrectly ordered updates
(define (process-updates updates rules)
  (define correct-updates
    (filter (λ (update) (is-update-ordered update rules)) updates))
  (define correct-middle-pages
    (map get-middle-page correct-updates))
  (define sum-correct-middle
    (sum-list correct-middle-pages))
  
  (define incorrect-updates
    (map (λ (update) (topological-sort-update update rules))
         (filter (λ (update) (not (is-update-ordered update rules))) updates)))
  (define incorrect-middle-pages
    (map get-middle-page incorrect-updates))
  (define sum-incorrect-middle
    (sum-list incorrect-middle-pages))
  
  (values sum-correct-middle sum-incorrect-middle))

;; Step 10: Main function to orchestrate the processing
(define (main [file-path "input.txt"])
  (define content (read-file file-path))
  (define-values (rules-section updates-section) (split-sections content))
  (define rules (parse-rules rules-section))
  (define updates (parse-updates updates-section))
  (define-values (sum-correct sum-incorrect) (process-updates updates rules))
  (printf "Sum of middle pages of correctly ordered updates: ~a\n" sum-correct)
  (printf "Sum of middle pages of corrected updates: ~a\n" sum-incorrect))

;; Execute the main function
(main)
